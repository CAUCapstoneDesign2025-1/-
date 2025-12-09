package com.example.uicomparison2.models

data class UISnapshot(
    val packageName: String,
    val activityName: String?,
    val visibleViews: List<String>,
    val textNodes: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)
