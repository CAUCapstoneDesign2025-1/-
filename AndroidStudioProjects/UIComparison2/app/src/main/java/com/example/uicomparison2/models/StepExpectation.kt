package com.example.uicomparison2.models

data class StepExpectation(
    val expectedPackage: String,
    val expectedActivity: String? = null,
    val expectedKeyViews: List<KeyView> = emptyList()
)
