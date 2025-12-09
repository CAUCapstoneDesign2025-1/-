package com.example.uicomparison2

import android.util.Log
import com.example.uicomparison2.models.MobileGPTStep
import com.example.uicomparison2.models.Step
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URL

/**
 * MobileGPT 서버에서 HTTP API를 통해 세션 및 Step 로드
 */
object CurriculumLoader {

    private const val TAG = "CurriculumLoader"

    // MobileGPT 서버 주소 (에뮬레이터는 10.0.2.2, 실제 디바이스는 PC IP 사용)
    private const val SERVER_URL = "http://10.0.2.2:5001"

    /**
     * 커리큘럼 세션 목록 가져오기
     */
    fun getCurriculumSessions(): List<String> {
        return try {
            val url = URL("$SERVER_URL/api/list_sessions")
            val response = url.readText()
            val gson = Gson()

            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            val data: Map<String, List<String>> = gson.fromJson(response, type)

            data["sessions"]?.sorted()?.reversed() ?: emptyList() // 최신순 정렬
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sessions", e)
            emptyList()
        }
    }

    /**
     * 특정 세션의 Step 목록 가져오기 (파일명)
     */
    fun getStepNamesInSession(sessionName: String): List<String> {
        return try {
            val steps = loadStepsFromSession(sessionName)
            steps.map { "step${it.stepId}.json" }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading step names", e)
            emptyList()
        }
    }

    /**
     * 세션의 모든 Step 로드
     */
    fun loadStepsFromSession(sessionName: String): List<Step> {
        return try {
            val url = URL("$SERVER_URL/api/get_steps/$sessionName")
            val response = url.readText()
            val gson = Gson()

            val type = object : TypeToken<Map<String, List<MobileGPTStep>>>() {}.type
            val data: Map<String, List<MobileGPTStep>> = gson.fromJson(response, type)

            data["steps"]?.map { it.toUIComparisonStep() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading steps from session: $sessionName", e)
            emptyList()
        }
    }

    /**
     * 특정 Step 로드 (index 기반)
     */
    fun loadSingleStep(sessionName: String, stepIndex: Int): Step? {
        return try {
            val steps = loadStepsFromSession(sessionName)
            if (stepIndex in steps.indices) {
                steps[stepIndex]
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading single step", e)
            null
        }
    }

    /**
     * 세션 정보 가져오기 (UI 표시용)
     */
    fun getSessionInfo(sessionName: String): SessionInfo {
        val steps = loadStepsFromSession(sessionName)
        val timestamp = sessionName.replace("session_", "")

        return SessionInfo(
            name = sessionName,
            displayName = formatSessionName(timestamp),
            stepCount = steps.size,
            timestamp = timestamp
        )
    }

    /**
     * 세션 이름 포맷팅
     */
    private fun formatSessionName(timestamp: String): String {
        // session_20251119_105345 -> 2025-11-19 10:53:45
        return try {
            val year = timestamp.substring(0, 4)
            val month = timestamp.substring(4, 6)
            val day = timestamp.substring(6, 8)
            val hour = timestamp.substring(9, 11)
            val minute = timestamp.substring(11, 13)
            val second = timestamp.substring(13, 15)

            "$year-$month-$day $hour:$minute:$second"
        } catch (e: Exception) {
            timestamp
        }
    }

    data class SessionInfo(
        val name: String,
        val displayName: String,
        val stepCount: Int,
        val timestamp: String
    )
}
