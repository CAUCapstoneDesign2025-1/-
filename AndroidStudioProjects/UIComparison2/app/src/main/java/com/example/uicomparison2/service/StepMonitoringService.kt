package com.example.uicomparison2.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.uicomparison2.FloatingWindowManager
import com.example.uicomparison2.ServerLogger
import com.example.uicomparison2.StepMatcher
import com.example.uicomparison2.models.*
import android.os.Handler
import android.os.Looper

class StepMonitoringService : AccessibilityService() {

    companion object {
        private const val TAG = "StepMonitoringService"
        private const val DEBOUNCE_DELAY = 150L // 150ms
        private const val FROZEN_CHECK_DELAY = 1000L // 1초
    }

    // Step 매칭 엔진
    private val stepMatcher = StepMatcher()

    // 플로팅 윈도우 매니저
    private var floatingWindowManager: FloatingWindowManager? = null

    // 현재 진행 중인 Step 리스트 (예시)
    private var currentSteps = mutableListOf<Step>()
    private var currentStepIndex = 0

    // UI Snapshot 디바운스를 위한 핸들러
    private val snapshotHandler = Handler(Looper.getMainLooper())
    private var snapshotRunnable: Runnable? = null

    // 마지막 클릭 정보
    private var lastClickedViewId: String? = null

    // 오류 카운터 (같은 오류가 연속으로 발생하는지 추적)
    private var consecutiveErrorCount = 0
    private var lastErrorType: ErrorType = ErrorType.NONE
    private val ERROR_THRESHOLD = 5 // 5번 연속으로 같은 오류가 발생해야 실제 오류로 판단

    // SharedPreferences listener
    private val sharedPrefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "tracking_active") {
            Log.d(TAG, "tracking_active changed, reloading steps...")
            loadStepsFromPreferences()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "StepMonitoringService connected")

        // 서버에 서비스 시작 알림
        ServerLogger.logServiceStatus("✅ 서비스 시작됨 (Accessibility Service 활성화)")

        // Overlay 권한 확인
        if (!canDrawOverlays()) {
            Log.e(TAG, "Overlay permission not granted. Cannot show floating window.")
            ServerLogger.logServiceStatus("❌ 오류: Overlay 권한 없음")
            return
        }

        // 플로팅 윈도우 초기화 (아직 표시하지 않음)
        floatingWindowManager = FloatingWindowManager(this)

        // Step 네비게이션 버튼 콜백 설정
        floatingWindowManager?.onPrevStepClicked = {
            moveToPreviousStep()
        }
        floatingWindowManager?.onNextStepClicked = {
            moveToNextStep()
        }

        // 서비스 시작 시 tracking 무조건 비활성화
        val sharedPref = getSharedPreferences("step_tracker", android.content.Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("tracking_active", false)
            apply()
        }

        ServerLogger.logServiceStatus("⏸️ 플로팅 윈도우 준비 완료 ('Start Tracking' 버튼을 눌러주세요)")

        // Register SharedPreferences listener to detect when tracking starts
        sharedPref.registerOnSharedPreferenceChangeListener(sharedPrefListener)

        Log.d(TAG, "Waiting for 'Start Tracking' button press...")
    }

    /**
     * Check if overlay permission is granted
     */
    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // 이벤트 타입 필터링
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleClickEvent(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleTextChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleContentChanged(event)
            }
        }
    }

    /**
     * 클릭 이벤트 처리
     */
    private fun handleClickEvent(event: AccessibilityEvent) {
        val viewId = event.source?.viewIdResourceName
        Log.d(TAG, "Click event: viewId=$viewId")

        lastClickedViewId = viewId
        stepMatcher.recordClick(viewId)

        // 클릭 시 즉시 스냅샷 생성 (debounce 없음)
        createAndProcessSnapshot()
    }

    /**
     * 화면 전환 이벤트 처리
     */
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        Log.d(TAG, "Window state changed: ${event.packageName} / ${event.className}")

        // 화면 전환 시 즉시 Snapshot 생성
        scheduleSnapshotCreation()
    }

    /**
     * 텍스트 변경 이벤트 처리
     */
    private fun handleTextChanged(event: AccessibilityEvent) {
        Log.d(TAG, "Text changed: ${event.text}")

        // 텍스트 입력 시 즉시 스냅샷 생성 (debounce 없음)
        createAndProcessSnapshot()
    }

    /**
     * 컨텐츠 변경 이벤트 처리 (TYPE_WINDOW_CONTENT_CHANGED - 2048)
     * 이 이벤트는 매우 빈번하게 발생하므로 디바운싱 필수
     */
    private fun handleContentChanged(event: AccessibilityEvent) {
        // 디바운스를 통해 UI가 안정된 후에만 처리
        scheduleSnapshotCreation()
    }

    /**
     * UI Snapshot 생성 스케줄링 (150ms 디바운스)
     * 연속적인 이벤트가 발생하면 타이머를 리셋하고,
     * 150ms 동안 추가 이벤트가 없을 때만 Snapshot 생성
     */
    private fun scheduleSnapshotCreation() {
        // 기존 스케줄 취소
        snapshotRunnable?.let { snapshotHandler.removeCallbacks(it) }

        // 새로운 스케줄 등록
        snapshotRunnable = Runnable {
            createAndProcessSnapshot()
        }
        snapshotHandler.postDelayed(snapshotRunnable!!, DEBOUNCE_DELAY)
    }

    /**
     * UI Snapshot 생성 및 Step 매칭 처리
     */
    private fun createAndProcessSnapshot() {
        // 현재 Step이 이미 완료되었으면 로그 수집 및 분석하지 않음
        if (currentStepIndex < currentSteps.size && currentSteps[currentStepIndex].isCompleted) {
            Log.d(TAG, "✅ 현재 Step이 이미 완료됨. 스냅샷 수집 건너뜀")
            return
        }

        val snapshot = createUISnapshot()
        Log.d(TAG, "📸 Snapshot: pkg=${snapshot.packageName}, views=${snapshot.visibleViews.size}, texts=${snapshot.textNodes.size}")

        // 서버에 스냅샷 정보 전송
        ServerLogger.logSnapshot(snapshot.packageName, snapshot.visibleViews.size, snapshot.textNodes.size)

        // Snapshot 업데이트 (frozen screen 감지용)
        stepMatcher.updateSnapshot(snapshot)

        // 현재 Step과 비교
        if (currentStepIndex < currentSteps.size) {
            val currentStep = currentSteps[currentStepIndex]

            Log.d(TAG, "🔍 Checking Step ${currentStepIndex + 1}/${currentSteps.size}: ${currentStep.stepName}")
            Log.d(TAG, "   Expected package: ${currentStep.expectation.expectedPackage}")
            Log.d(TAG, "   Current package: ${snapshot.packageName}")
            Log.d(TAG, "   Expected keyViews: ${currentStep.expectation.expectedKeyViews.size}")

            // 서버에 Step 확인 정보 전송
            ServerLogger.logStepCheck(
                currentStepIndex + 1,
                currentSteps.size,
                currentStep.stepName,
                currentStep.expectation.expectedPackage,
                snapshot.packageName
            )

            // 현재 Step 정보 업데이트
            floatingWindowManager?.setCurrentStep(
                currentStepIndex + 1,
                currentSteps.size,
                currentStep.stepName
            )

            // 확인 중 상태 표시
            floatingWindowManager?.setStatus(FloatingWindowManager.StepStatus.CHECKING)

            // Step 매칭 확인
            if (stepMatcher.isStepMatched(currentStep, snapshot)) {
                Log.d(TAG, "✅ Step ${currentStep.stepId} matched successfully!")

                // 오류 카운터 리셋
                consecutiveErrorCount = 0
                lastErrorType = ErrorType.NONE

                // "현재 단계 확인됨!" 상태 표시
                floatingWindowManager?.setStatus(FloatingWindowManager.StepStatus.MATCHED)

                // 매칭 성공 시 서버에 알림 (한 번만)
                if (!currentStep.isCompleted) {
                    currentStep.isCompleted = true
                    ServerLogger.logStepMatched(currentStepIndex + 1, currentStep.stepName)
                }

                // ✅ 자동으로 다음 Step으로 가지 않음 - 사용자가 수동으로 네비게이션
                // ✅ 오류 분석도 하지 않음 (매칭 성공했으므로)

            } else {
                // 매칭 실패 - 대기 상태로 복귀
                floatingWindowManager?.setStatus(FloatingWindowManager.StepStatus.WAITING)

                // 오류 감지
                val errorType = stepMatcher.detectError(currentStep, snapshot, lastClickedViewId)
                if (errorType != ErrorType.NONE) {
                    // WRONG_CLICK은 즉시 보고 (카운트 없이)
                    if (errorType == ErrorType.WRONG_CLICK) {
                        Log.w(TAG, "❌ Wrong click detected immediately: $lastClickedViewId")
                        ServerLogger.logError(currentStepIndex + 1, errorType.name)
                        // 카운터는 리셋
                        consecutiveErrorCount = 0
                        lastErrorType = ErrorType.NONE
                    } else {
                        // WRONG_APP, FROZEN_SCREEN은 연속 카운트 체크
                        if (errorType == lastErrorType) {
                            consecutiveErrorCount++
                            Log.d(TAG, "⚠️ Consecutive error count: $consecutiveErrorCount/$ERROR_THRESHOLD for $errorType")

                            // 임계값을 넘으면 서버에 보고
                            if (consecutiveErrorCount >= ERROR_THRESHOLD) {
                                Log.w(TAG, "❌ Confirmed error after $consecutiveErrorCount attempts: $errorType")
                                ServerLogger.logError(currentStepIndex + 1, errorType.name)
                                // 이미 보고했으므로 카운터 리셋 (중복 보고 방지)
                                consecutiveErrorCount = 0
                            }
                        } else {
                            // 다른 오류 타입으로 변경됨 - 카운터 리셋
                            lastErrorType = errorType
                            consecutiveErrorCount = 1
                        }
                    }
                } else {
                    // 오류 없음 - 카운터 리셋
                    consecutiveErrorCount = 0
                    lastErrorType = ErrorType.NONE
                }
            }
        } else {
            Log.d(TAG, "All steps completed, ignoring events")
        }

        // 클릭 정보 리셋
        lastClickedViewId = null
    }

    /**
     * 현재 화면의 UI Snapshot 생성
     */
    private fun createUISnapshot(): UISnapshot {
        val rootNode = rootInActiveWindow
        val packageName = rootNode?.packageName?.toString() ?: ""
        val activityName = extractActivityName(rootNode)
        val visibleViews = mutableListOf<String>()
        val textNodes = mutableListOf<String>()

        // UI 트리 순회하며 visible한 View와 Text 수집
        rootNode?.let { traverseNode(it, visibleViews, textNodes) }

        return UISnapshot(
            packageName = packageName,
            activityName = activityName,
            visibleViews = visibleViews,
            textNodes = textNodes
        )
    }

    /**
     * AccessibilityNodeInfo 트리 순회
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        visibleViews: MutableList<String>,
        textNodes: MutableList<String>
    ) {
        // Visible한 노드만 처리
        if (!node.isVisibleToUser) {
            return
        }

        // ViewId 수집
        node.viewIdResourceName?.let { viewId ->
            if (viewId.isNotEmpty()) {
                visibleViews.add(viewId)
            }
        }

        // Text 수집
        node.text?.toString()?.let { text ->
            if (text.isNotEmpty()) {
                textNodes.add(text)
            }
        }

        // 자식 노드 순회
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, visibleViews, textNodes)
            }
        }
    }

    /**
     * Activity 이름 추출
     */
    private fun extractActivityName(rootNode: AccessibilityNodeInfo?): String? {
        return rootNode?.className?.toString()
    }

    /**
     * Step 오류 처리
     */
    private fun handleStepError(step: Step, errorType: ErrorType) {
        when (errorType) {
            ErrorType.WRONG_APP -> {
                Log.e(TAG, "Wrong app detected for step ${step.stepId}")
                // 예: 알림 표시, 재시도 로직 등
            }
            ErrorType.FROZEN_SCREEN -> {
                Log.e(TAG, "Screen frozen for step ${step.stepId}")
                // 예: 화면 리프레시, 사용자 알림 등
            }
            ErrorType.WRONG_CLICK -> {
                Log.e(TAG, "Wrong click detected for step ${step.stepId}")
                // 예: 올바른 클릭 위치 안내 등
            }
            ErrorType.NONE -> {
                // 오류 없음
            }
        }
    }

    /**
     * Load steps from SharedPreferences
     */
    private fun loadStepsFromPreferences() {
        val sharedPref = getSharedPreferences("step_tracker", android.content.Context.MODE_PRIVATE)
        val trackingActive = sharedPref.getBoolean("tracking_active", false)
        val stepsJson = sharedPref.getString("steps_json", null)

        if (trackingActive && stepsJson != null) {
            try {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<Step>>() {}.type
                currentSteps = gson.fromJson(stepsJson, type)
                currentStepIndex = 0

                // 모든 Step의 완료 상태 리셋
                currentSteps.forEach { it.isCompleted = false }

                // 오류 카운터 리셋
                consecutiveErrorCount = 0
                lastErrorType = ErrorType.NONE

                Log.d(TAG, "Loaded ${currentSteps.size} steps from preferences (fresh start)")

                // 플로팅 윈도우 표시 및 초기 Step 정보 업데이트
                if (currentSteps.isNotEmpty()) {
                    val firstStep = currentSteps[0]

                    // 기존 윈도우가 있으면 숨기고 새로 표시
                    floatingWindowManager?.hide()
                    floatingWindowManager?.show()
                    floatingWindowManager?.setCurrentStep(1, currentSteps.size, firstStep.stepName)
                    floatingWindowManager?.setStatus(FloatingWindowManager.StepStatus.WAITING)

                    Log.d(TAG, "First step: ${firstStep.stepName}, waiting for user action...")

                    // 서버에 Step 로드 완료 알림
                    ServerLogger.logServiceStatus("🔄 새로운 추적 세션 시작!")
                    ServerLogger.logServiceStatus("✅ ${currentSteps.size}개 Step 로드 완료. 첫 번째 Step: ${firstStep.stepName}")
                    ServerLogger.logServiceStatus("🚀 플로팅 윈도우 표시됨 - 추적 시작!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading steps from preferences", e)
                ServerLogger.logServiceStatus("❌ Step 로드 오류: ${e.message}")
                currentSteps = mutableListOf()
            }
        } else {
            Log.d(TAG, "No active tracking session")
            ServerLogger.logServiceStatus("⏸️ 활성 추적 세션 없음 (앱에서 'Start Tracking' 버튼을 눌러주세요)")
            currentSteps = mutableListOf()

            // 추적이 활성화되지 않았으면 플로팅 윈도우 숨김
            floatingWindowManager?.hide()
        }
    }

    /**
     * 이전 Step으로 이동
     */
    private fun moveToPreviousStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--
            val currentStep = currentSteps[currentStepIndex]

            // 새로운 Step으로 이동하면 완료 상태 리셋
            currentStep.isCompleted = false

            floatingWindowManager?.setCurrentStep(
                currentStepIndex + 1,
                currentSteps.size,
                currentStep.stepName
            )
            floatingWindowManager?.setStatus(FloatingWindowManager.StepStatus.WAITING)

            ServerLogger.logServiceStatus("⬅️ 수동으로 이전 Step으로 이동: ${currentStep.stepName}")
            Log.d(TAG, "Moved to previous step: ${currentStepIndex + 1}/${currentSteps.size}")
        } else {
            Log.d(TAG, "Already at first step, cannot go back")
        }
    }

    /**
     * 다음 Step으로 이동
     */
    private fun moveToNextStep() {
        if (currentStepIndex < currentSteps.size - 1) {
            currentStepIndex++
            val currentStep = currentSteps[currentStepIndex]

            // 새로운 Step으로 이동하면 완료 상태 리셋
            currentStep.isCompleted = false

            floatingWindowManager?.setCurrentStep(
                currentStepIndex + 1,
                currentSteps.size,
                currentStep.stepName
            )
            floatingWindowManager?.setStatus(FloatingWindowManager.StepStatus.WAITING)

            ServerLogger.logServiceStatus("➡️ 수동으로 다음 Step으로 이동: ${currentStep.stepName}")
            Log.d(TAG, "Moved to next step: ${currentStepIndex + 1}/${currentSteps.size}")
        } else {
            Log.d(TAG, "Already at last step, cannot go forward")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "StepMonitoringService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotHandler.removeCallbacksAndMessages(null)

        // Unregister SharedPreferences listener
        val sharedPref = getSharedPreferences("step_tracker", android.content.Context.MODE_PRIVATE)
        sharedPref.unregisterOnSharedPreferenceChangeListener(sharedPrefListener)

        // Clear tracking session
        with(sharedPref.edit()) {
            putBoolean("tracking_active", false)
            apply()
        }

        // 플로팅 윈도우 제거
        floatingWindowManager?.hide()
        floatingWindowManager = null

        Log.d(TAG, "StepMonitoringService destroyed")
    }
}
