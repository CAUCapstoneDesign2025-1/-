package com.example.uicomparison2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import android.widget.LinearLayout
import android.widget.TextView
import com.example.uicomparison2.models.ErrorType

class FloatingWindowManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    // UI Components
    private var errorBanner: LinearLayout? = null
    private var errorTitle: TextView? = null
    private var errorMessage: TextView? = null
    private var stepProgress: TextView? = null
    private var stepInstruction: TextView? = null
    private var statusIcon: TextView? = null
    private var statusText: TextView? = null
    private var prevStepButton: TextView? = null
    private var nextStepButton: TextView? = null
    private var expandedContent: LinearLayout? = null
    private var minimizeButton: TextView? = null

    private var isShowing = false
    private var isExpanded = true
    private val handler = Handler(Looper.getMainLooper())

    // 드래그 관련 변수
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // Step navigation callbacks
    var onPrevStepClicked: (() -> Unit)? = null
    var onNextStepClicked: (() -> Unit)? = null

    enum class StepStatus {
        WAITING,     // 👀 올바른 화면으로 이동하세요
        CHECKING,    // 🔍 Step Matching...
        MATCHED,     // ✅ 현재 단계 확인됨!
        COMPLETED    // ✅ 완료!
    }

    /**
     * 플로팅 윈도우 표시
     */
    @SuppressLint("InflateParams")
    fun show() {
        if (isShowing) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(context).inflate(R.layout.floating_step_monitor, null)

        // View 참조
        errorBanner = floatingView?.findViewById(R.id.errorBanner)
        errorTitle = floatingView?.findViewById(R.id.errorTitle)
        errorMessage = floatingView?.findViewById(R.id.errorMessage)
        stepProgress = floatingView?.findViewById(R.id.stepProgress)
        stepInstruction = floatingView?.findViewById(R.id.stepInstruction)
        statusIcon = floatingView?.findViewById(R.id.statusIcon)
        statusText = floatingView?.findViewById(R.id.statusText)
        prevStepButton = floatingView?.findViewById(R.id.prevStepButton)
        nextStepButton = floatingView?.findViewById(R.id.nextStepButton)
        expandedContent = floatingView?.findViewById(R.id.expandedContent)
        minimizeButton = floatingView?.findViewById(R.id.minimizeButton)

        // 최소화 버튼
        minimizeButton?.setOnClickListener {
            toggleExpanded()
        }

        // 닫기 버튼
        floatingView?.findViewById<TextView>(R.id.closeButton)?.setOnClickListener {
            hide()
        }

        // 이전 Step 버튼
        prevStepButton?.setOnClickListener {
            onPrevStepClicked?.invoke()
        }

        // 다음 Step 버튼
        nextStepButton?.setOnClickListener {
            onNextStepClicked?.invoke()
        }

        // WindowManager 파라미터
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        // 드래그 가능하도록 터치 리스너 설정
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 플로팅 윈도우 숨기기
     */
    fun hide() {
        if (!isShowing) return

        try {
            handler.removeCallbacksAndMessages(null)
            windowManager?.removeView(floatingView)
            isShowing = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 현재 Step 설정
     */
    fun setCurrentStep(currentStep: Int, totalSteps: Int, instruction: String) {
        handler.post {
            stepProgress?.text = "Step $currentStep/$totalSteps"
            stepInstruction?.text = instruction
            setStatus(StepStatus.WAITING)
        }
    }

    /**
     * 상태 업데이트
     */
    fun setStatus(status: StepStatus) {
        handler.post {
            when (status) {
                StepStatus.WAITING -> {
                    statusIcon?.text = "👀"
                    statusText?.text = "올바른 화면으로 이동하세요"
                }
                StepStatus.CHECKING -> {
                    statusIcon?.text = "🔍"
                    statusText?.text = "Step Matching..."
                }
                StepStatus.MATCHED -> {
                    statusIcon?.text = "✅"
                    statusText?.text = "현재 단계 확인됨!"
                }
                StepStatus.COMPLETED -> {
                    statusIcon?.text = "🎉"
                    statusText?.text = "완료!"
                }
            }
        }
    }

    /**
     * Step 성공 애니메이션
     */
    fun animateSuccess(onComplete: (() -> Unit)? = null) {
        handler.post {
            setStatus(StepStatus.COMPLETED)

            // Scale animation
            val scaleAnim = ScaleAnimation(
                1.0f, 1.2f, 1.0f, 1.2f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 300
                repeatCount = 1
                repeatMode = ScaleAnimation.REVERSE
            }

            statusIcon?.startAnimation(scaleAnim)

            // 1초 후 다음 Step으로
            handler.postDelayed({
                onComplete?.invoke()
            }, 1000)
        }
    }

    /**
     * 오류 배너 표시
     */
    fun showError(errorType: ErrorType, autoHideAfterMs: Long = 3000) {
        handler.post {
            val (title, message) = when (errorType) {
                ErrorType.WRONG_APP -> {
                    "❌ 다른 앱이 열렸어요" to "올바른 앱 화면으로 돌아가주세요"
                }
                ErrorType.FROZEN_SCREEN -> {
                    "⚠️ 화면이 멈췄어요" to "화면에서 변화를 감지할 수 없어요. 다시 시도해주세요"
                }
                ErrorType.WRONG_CLICK -> {
                    "⚠️ 다른 버튼을 눌렀어요" to "안내된 버튼을 눌러주세요"
                }
                ErrorType.NONE -> {
                    "⚠️ 오류 발생" to "알 수 없는 오류가 발생했어요"
                }
            }

            errorTitle?.text = title
            errorMessage?.text = message
            errorBanner?.visibility = View.VISIBLE

            // Fade in animation
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 300
            }
            errorBanner?.startAnimation(fadeIn)

            // Auto hide
            if (autoHideAfterMs > 0) {
                handler.postDelayed({
                    hideError()
                }, autoHideAfterMs)
            }
        }
    }

    /**
     * 오류 배너 숨기기
     */
    fun hideError() {
        handler.post {
            val fadeOut = AlphaAnimation(1f, 0f).apply {
                duration = 300
            }
            errorBanner?.startAnimation(fadeOut)

            handler.postDelayed({
                errorBanner?.visibility = View.GONE
            }, 300)
        }
    }

    /**
     * 모든 Step 완료
     */
    fun showAllCompleted() {
        handler.post {
            stepProgress?.text = "완료!"
            stepInstruction?.text = "모든 단계를 완료했습니다 🎉"
            setStatus(StepStatus.COMPLETED)
        }
    }

    /**
     * 초기 메시지 (추적 시작 전)
     */
    fun showInitialMessage(message: String) {
        handler.post {
            stepProgress?.text = ""
            stepInstruction?.text = message
            statusIcon?.text = "ℹ️"
            statusText?.text = ""
        }
    }

    fun isVisible(): Boolean = isShowing

    /**
     * 플로팅 윈도우 접기/펼치기 토글
     */
    private fun toggleExpanded() {
        isExpanded = !isExpanded
        handler.post {
            if (isExpanded) {
                // 펼치기
                expandedContent?.visibility = View.VISIBLE
                minimizeButton?.text = "—"
            } else {
                // 접기
                expandedContent?.visibility = View.GONE
                minimizeButton?.text = "+"
            }
        }
    }

    // Legacy methods - deprecated but kept for compatibility
    @Deprecated("Use setCurrentStep instead")
    fun updateCurrentStep(currentStep: Int, totalSteps: Int) {
        stepProgress?.text = "Step $currentStep/$totalSteps"
    }

    @Deprecated("Use animateSuccess instead")
    fun addSuccessMessage(stepId: Int, stepName: String) {
        animateSuccess()
    }

    @Deprecated("Use showError instead")
    fun addErrorMessage(stepId: Int, stepName: String, errorType: ErrorType) {
        showError(errorType)
    }

    @Deprecated("Use showInitialMessage instead")
    fun addInfoMessage(message: String) {
        showInitialMessage(message)
    }

    @Deprecated("Not used in new UI")
    fun addSnapshotMessage(packageName: String) {
        // No-op in new UI
    }
}
