package com.example.uicomparison2.models

data class Step(
    val stepId: Int,
    val stepName: String,
    val expectation: StepExpectation,
    var isCompleted: Boolean = false,
    var errorType: ErrorType = ErrorType.NONE
)
