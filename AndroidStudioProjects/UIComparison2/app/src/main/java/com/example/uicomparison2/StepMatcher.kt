package com.example.uicomparison2

import com.example.uicomparison2.models.ErrorType
import com.example.uicomparison2.models.Step
import com.example.uicomparison2.models.UISnapshot

class StepMatcher {

    private var lastClickTimestamp: Long = 0
    private var lastClickedViewId: String? = null
    private var lastSnapshot: UISnapshot? = null

    /**
     * Step과 현재 UI Snapshot을 비교하여 매칭 여부 판단
     * 더 유연한 매칭 로직 적용
     */
    fun isStepMatched(step: Step, snapshot: UISnapshot): Boolean {
        // 1. 패키지 확인 (대소문자 무시, 부분 일치)
        if (!snapshot.packageName.contains(step.expectation.expectedPackage, ignoreCase = true)) {
            android.util.Log.d("StepMatcher", "❌ Package mismatch: expected=${step.expectation.expectedPackage}, actual=${snapshot.packageName}")
            return false
        }

        // 2. Activity 확인은 생략 (너무 엄격함)
        // Activity는 앱 내부 구조에 따라 다양할 수 있음

        // 3. 핵심 View 중 하나라도 화면에 보이면 성공 (유연한 매칭)
        var matchedCount = 0
        for (keyView in step.expectation.expectedKeyViews) {
            // viewId로 확인 (부분 일치)
            if (keyView.viewId != null) {
                val matched = snapshot.visibleViews.any { visibleViewId ->
                    // 정확히 일치하거나
                    visibleViewId == keyView.viewId ||
                    // viewId의 끝부분이 일치하거나 (예: "button" vs "com.app:id/button")
                    visibleViewId.endsWith("/${keyView.viewId}") ||
                    visibleViewId.endsWith(":id/${keyView.viewId}") ||
                    // 부분 문자열로 포함
                    visibleViewId.contains(keyView.viewId, ignoreCase = true)
                }
                if (matched) {
                    android.util.Log.d("StepMatcher", "✅ ViewId matched: ${keyView.viewId}")
                    matchedCount++
                    return true // 하나라도 매칭되면 성공
                }
            }

            // text로 확인 (부분 일치, 대소문자 무시)
            if (keyView.text != null) {
                val matched = snapshot.textNodes.any { textNode ->
                    textNode.contains(keyView.text, ignoreCase = true) ||
                    keyView.text.contains(textNode, ignoreCase = true)
                }
                if (matched) {
                    android.util.Log.d("StepMatcher", "✅ Text matched: ${keyView.text}")
                    matchedCount++
                    return true // 하나라도 매칭되면 성공
                }
            }
        }

        android.util.Log.d("StepMatcher", "❌ No keyViews matched (checked ${step.expectation.expectedKeyViews.size} keyViews)")
        return false
    }

    /**
     * 오류 감지 로직 (완화된 체크)
     */
    fun detectError(step: Step, snapshot: UISnapshot, clickedViewId: String? = null): ErrorType {
        // Case 1: 잘못된 앱으로 이동 (시작 일치로 체크 - 더 관대함)
        // 예: expected="com.google.android.youtube" → "com.google.android.youtube.tv" OK
        val packageMatches = snapshot.packageName.startsWith(step.expectation.expectedPackage, ignoreCase = true) ||
                             step.expectation.expectedPackage.startsWith(snapshot.packageName, ignoreCase = true)
        if (!packageMatches) {
            return ErrorType.WRONG_APP
        }

        // Case 2: 화면 정체/멈춤
        // 조건: 클릭 발생 후 3초 동안 snapshot 변화 없음 (완화됨)
        if (isFrozenScreen(snapshot)) {
            return ErrorType.FROZEN_SCREEN
        }

        // Case 3: 잘못된 클릭
        // 조건: 클릭된 viewId가 expectedKeyViews에 없음
        if (clickedViewId != null && isWrongClick(step, clickedViewId)) {
            return ErrorType.WRONG_CLICK
        }

        return ErrorType.NONE
    }

    /**
     * 클릭 이벤트 기록
     */
    fun recordClick(viewId: String?) {
        lastClickTimestamp = System.currentTimeMillis()
        lastClickedViewId = viewId
    }

    /**
     * 화면 정체 여부 확인
     * 클릭 후 3초 동안 UI 변화가 없으면 Frozen으로 판정 (완화됨)
     */
    private fun isFrozenScreen(snapshot: UISnapshot): Boolean {
        // 클릭이 발생하지 않았으면 정체로 판정하지 않음
        if (lastClickTimestamp == 0L) {
            return false
        }

        val timeSinceClick = System.currentTimeMillis() - lastClickTimestamp

        // 클릭 후 3초 이상 경과 (기존 1초 → 3초로 완화)
        if (timeSinceClick >= 3000) {
            // 이전 snapshot과 동일한지 확인
            if (lastSnapshot != null &&
                snapshot.packageName == lastSnapshot!!.packageName &&
                snapshot.activityName == lastSnapshot!!.activityName &&
                snapshot.visibleViews == lastSnapshot!!.visibleViews) {
                return true
            }
        }

        return false
    }

    /**
     * 잘못된 클릭 여부 확인
     */
    private fun isWrongClick(step: Step, clickedViewId: String): Boolean {
        // expectedKeyViews에 클릭된 viewId가 있는지 확인
        for (keyView in step.expectation.expectedKeyViews) {
            if (keyView.viewId == clickedViewId) {
                return false // 올바른 클릭
            }
        }
        return true // 잘못된 클릭
    }

    /**
     * 현재 snapshot 저장 (frozen screen 감지용)
     */
    fun updateSnapshot(snapshot: UISnapshot) {
        lastSnapshot = snapshot
    }
}
