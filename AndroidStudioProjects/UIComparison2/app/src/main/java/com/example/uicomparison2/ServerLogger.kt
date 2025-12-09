package com.example.uicomparison2

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 서버로 실시간 추적 로그를 전송하는 헬퍼 클래스
 */
object ServerLogger {

    private const val TAG = "ServerLogger"
    private const val SERVER_URL = "http://10.0.2.2:5001/api/log_tracking"

    /**
     * 서버에 로그 전송 (백그라운드 스레드에서 실행)
     */
    private fun sendLog(data: Map<String, Any?>) {
        Thread {
            try {
                val url = URL(SERVER_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonObject = JSONObject(data)
                val jsonString = jsonObject.toString()

                conn.outputStream.use { it.write(jsonString.toByteArray()) }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Log sent successfully: ${data["type"]}")
                } else {
                    Log.w(TAG, "Failed to send log: $responseCode")
                }

                conn.disconnect()
            } catch (e: Exception) {
                // 네트워크 오류는 조용히 무시 (서버가 꺼져있을 수 있음)
                Log.d(TAG, "Failed to send log: ${e.message}")
            }
        }.start()
    }

    /**
     * 서비스 상태 로그
     */
    fun logServiceStatus(status: String) {
        sendLog(mapOf(
            "type" to "service_status",
            "status" to status
        ))
    }

    /**
     * 스냅샷 생성 로그
     */
    fun logSnapshot(packageName: String, viewCount: Int, textCount: Int) {
        sendLog(mapOf(
            "type" to "snapshot",
            "package" to packageName,
            "view_count" to viewCount,
            "text_count" to textCount
        ))
    }

    /**
     * Step 확인 로그
     */
    fun logStepCheck(
        stepNumber: Int,
        totalSteps: Int,
        stepName: String,
        expectedPackage: String,
        currentPackage: String
    ) {
        sendLog(mapOf(
            "type" to "step_check",
            "step_number" to stepNumber,
            "total_steps" to totalSteps,
            "step_name" to stepName,
            "expected_package" to expectedPackage,
            "current_package" to currentPackage
        ))
    }

    /**
     * Step 매칭 성공 로그
     */
    fun logStepMatched(stepNumber: Int, stepName: String) {
        sendLog(mapOf(
            "type" to "step_matched",
            "step_number" to stepNumber,
            "step_name" to stepName
        ))
    }

    /**
     * 오류 발생 로그
     */
    fun logError(stepNumber: Int, errorType: String) {
        sendLog(mapOf(
            "type" to "error",
            "step_number" to stepNumber,
            "error_type" to errorType
        ))
    }

    /**
     * 모든 Step 완료 로그
     */
    fun logAllCompleted() {
        sendLog(mapOf(
            "type" to "all_completed"
        ))
    }
}
