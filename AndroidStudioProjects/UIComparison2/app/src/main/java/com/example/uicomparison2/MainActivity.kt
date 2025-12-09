package com.example.uicomparison2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.example.uicomparison2.models.Step
import com.google.gson.Gson

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var enableButton: Button
    private lateinit var overlayButton: Button
    private lateinit var selectFileButton: Button
    private lateinit var startTrackingButton: Button
    private lateinit var selectedFileTextView: TextView
    private lateinit var stepInfoTextView: TextView

    private var loadedSteps: List<Step>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        enableButton = findViewById(R.id.enableButton)
        overlayButton = findViewById(R.id.overlayButton)
        selectFileButton = findViewById(R.id.selectFileButton)
        startTrackingButton = findViewById(R.id.startTrackingButton)
        selectedFileTextView = findViewById(R.id.selectedFileTextView)
        stepInfoTextView = findViewById(R.id.stepInfoTextView)

        enableButton.setOnClickListener {
            openAccessibilitySettings()
        }

        overlayButton.setOnClickListener {
            requestOverlayPermission()
        }

        selectFileButton.setOnClickListener {
            openFilePicker()
        }

        startTrackingButton.setOnClickListener {
            startTracking()
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    /**
     * Open curriculum selection dialog
     */
    private fun openFilePicker() {
        // Load data in background thread
        Thread {
            try {
                val sessions = CurriculumLoader.getCurriculumSessions()

                runOnUiThread {
                    if (sessions.isEmpty()) {
                        Toast.makeText(this, "No curriculum found. Please start server first.", Toast.LENGTH_LONG).show()
                    } else {
                        showCurriculumSelectionDialog(sessions)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Server connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /**
     * Show curriculum selection dialog
     */
    private fun showCurriculumSelectionDialog(sessions: List<String>) {
        Thread {
            val sessionInfos = sessions.map { CurriculumLoader.getSessionInfo(it) }
            val displayNames = sessionInfos.map { "${it.displayName} (${it.stepCount} steps)" }.toTypedArray()

            runOnUiThread {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Select Curriculum")
                    .setItems(displayNames) { _, which ->
                        val selectedSession = sessionInfos[which]
                        showStepSelectionDialog(selectedSession.name)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    /**
     * Show step selection dialog for selected curriculum
     */
    private fun showStepSelectionDialog(sessionName: String) {
        Thread {
            try {
                val stepNames = CurriculumLoader.getStepNamesInSession(sessionName)

                runOnUiThread {
                    if (stepNames.isEmpty()) {
                        Toast.makeText(this, "No steps found in this curriculum", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    val selectedSteps = BooleanArray(stepNames.size) { true } // Select all by default

                    android.app.AlertDialog.Builder(this)
                        .setTitle("Select Steps to Track")
                        .setMultiChoiceItems(stepNames.toTypedArray(), selectedSteps) { _, which, isChecked ->
                            selectedSteps[which] = isChecked
                        }
                        .setPositiveButton("Load Steps") { _, _ ->
                            loadSelectedSteps(sessionName, selectedSteps)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error loading steps: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * Load selected steps from curriculum
     */
    private fun loadSelectedSteps(sessionName: String, selectedSteps: BooleanArray) {
        Thread {
            try {
                val allSteps = CurriculumLoader.loadStepsFromSession(sessionName)
                val steps = mutableListOf<Step>()

                selectedSteps.forEachIndexed { index, isSelected ->
                    if (isSelected && index < allSteps.size) {
                        steps.add(allSteps[index])
                    }
                }

                // Get session info in background thread (before runOnUiThread)
                val sessionInfo = CurriculumLoader.getSessionInfo(sessionName)

                runOnUiThread {
                    if (steps.isEmpty()) {
                        Toast.makeText(this, "No steps selected", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    loadedSteps = steps

                    // Update UI
                    selectedFileTextView.text = "Session: ${sessionInfo.displayName}\n${steps.size} steps selected"
                    startTrackingButton.isEnabled = true

                    // Show step info
                    stepInfoTextView.visibility = android.view.View.VISIBLE
                    stepInfoTextView.text = "Loaded ${steps.size} steps:\n" +
                            steps.joinToString("\n") { "- Step ${it.stepId}: ${it.stepName}" }

                    Toast.makeText(this, "Loaded ${steps.size} steps", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error loading steps: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }


    /**
     * Start tracking - send steps to service and navigate to home
     */
    private fun startTracking() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_LONG).show()
            return
        }

        if (!canDrawOverlays()) {
            Toast.makeText(this, "Please grant Overlay Permission first", Toast.LENGTH_LONG).show()
            return
        }

        loadedSteps?.let { steps ->
            // Save steps to shared preferences for the service to read
            val sharedPref = getSharedPreferences("step_tracker", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("steps_json", Gson().toJson(steps))
                putBoolean("tracking_active", false) // 먼저 false로 설정
                apply()
            }

            // 잠시 대기 후 true로 변경하여 서비스가 리셋되도록 함
            android.os.Handler(mainLooper).postDelayed({
                with(sharedPref.edit()) {
                    putBoolean("tracking_active", true)
                    apply()
                }

                Toast.makeText(this, "Tracking started! Going to home screen...", Toast.LENGTH_SHORT).show()

                // Navigate to home screen after a short delay
                android.os.Handler(mainLooper).postDelayed({
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.addCategory(Intent.CATEGORY_HOME)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                }, 500)
            }, 100)
        }
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

    /**
     * Accessibility setting screen
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    /**
     * Request overlay permission
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    /**
     * Update service status and UI
     */
    private fun updateServiceStatus() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        val isOverlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        // Update button states
        overlayButton.isEnabled = !isOverlayEnabled
        selectFileButton.isEnabled = isAccessibilityEnabled && isOverlayEnabled

        val statusBuilder = StringBuilder()

        // Accessibility Service status
        if (isAccessibilityEnabled) {
            statusBuilder.append("✅ Accessibility Service: ENABLED\n")
        } else {
            statusBuilder.append("❌ Accessibility Service: DISABLED\n")
        }

        // Overlay permission status
        if (isOverlayEnabled) {
            statusBuilder.append("✅ Overlay Permission: GRANTED\n\n")
        } else {
            statusBuilder.append("❌ Overlay Permission: DENIED\n\n")
        }

        // Status message
        if (isAccessibilityEnabled && isOverlayEnabled) {
            statusBuilder.append("All permissions granted! Select a step file to begin.\n")
            enableButton.text = "Accessibility Settings"
            overlayButton.text = "Overlay Granted ✓"
        } else {
            statusBuilder.append("Please grant the required permissions:\n\n")
            if (!isAccessibilityEnabled) {
                statusBuilder.append("1. Enable Accessibility Service\n")
                statusBuilder.append("   - Find 'UIComparison2' in the list\n")
                statusBuilder.append("   - Toggle it ON\n\n")
            }
            if (!isOverlayEnabled) {
                statusBuilder.append("2. Grant Overlay Permission\n")
                statusBuilder.append("   - Tap button below\n")
                statusBuilder.append("   - Toggle 'Allow display over other apps' ON\n")
            }
            enableButton.text = if (isAccessibilityEnabled) "Accessibility Settings" else "Enable Accessibility Service"
            overlayButton.text = if (isOverlayEnabled) "Overlay Granted ✓" else "Allow Overlay Permission"
        }

        statusTextView.text = statusBuilder.toString()
    }

    /**
     * Check if AccessibilityService is enabled
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        val colonSplitter = enabledServices?.split(":")
        val expectedComponentName = "${packageName}/${packageName}.service.StepMonitoringService"

        return colonSplitter?.any { it.equals(expectedComponentName, ignoreCase = true) } ?: false
    }
}


