package com.example.uicomparison2.models

/**
 * MobileGPT에서 사용하는 Step 형식
 */
data class MobileGPTStep(
    val step: Int,
    val title: String?,
    val description: String?,
    val time: Long?,
    val eventType: Int?,
    val `package`: String?,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val bounds: String?
) {
    /**
     * MobileGPT Step을 UIComparison Step으로 변환
     */
    fun toUIComparisonStep(): Step {
        val keyViews = mutableListOf<KeyView>()

        // contentDescription이 있으면 KeyView에 추가
        contentDescription?.let { desc ->
            if (desc != "null" && desc.isNotEmpty()) {
                keyViews.add(KeyView(viewId = null, text = desc))
            }
        }

        // text가 있으면 파싱해서 KeyView에 추가
        text?.let { txt ->
            if (txt != "[]" && txt != "null" && txt.isNotEmpty()) {
                try {
                    // "[One UI Home]" 형식을 파싱
                    val cleanText = txt.replace("[", "").replace("]", "")
                    if (cleanText.isNotEmpty()) {
                        keyViews.add(KeyView(viewId = null, text = cleanText))
                    }
                } catch (e: Exception) {
                    // 파싱 실패 시 무시
                }
            }
        }

        // viewId가 있으면 KeyView에 추가
        viewId?.let { id ->
            if (id != "null" && id.isNotEmpty()) {
                keyViews.add(KeyView(viewId = id, text = null))
            }
        }

        // className을 기반으로 KeyView 추가 (fallback)
        if (keyViews.isEmpty()) {
            className?.let { clsName ->
                if (clsName != "null" && clsName.isNotEmpty()) {
                    // className만으로는 매칭이 어려우므로 title 사용
                    title?.let { keyViews.add(KeyView(viewId = null, text = it)) }
                }
            }
        }

        return Step(
            stepId = step,
            stepName = title ?: "Unknown Step",
            expectation = StepExpectation(
                expectedPackage = `package` ?: "",
                expectedActivity = className?.takeIf { it != "null" },
                expectedKeyViews = keyViews
            ),
            isCompleted = false,
            errorType = ErrorType.NONE
        )
    }
}