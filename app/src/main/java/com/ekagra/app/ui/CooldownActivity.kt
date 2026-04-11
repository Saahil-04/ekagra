package com.ekagra.app.ui

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.ekagra.app.accessibility.EkagraAccessibilityService
import com.ekagra.app.databinding.ActivityCooldownBinding

class CooldownActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCooldownBinding
    private var countDownTimer: CountDownTimer? = null
    private val totalSeconds = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) { /* blocked */ }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        binding = ActivityCooldownBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGoToDMs.isEnabled = false
        binding.btnGoToDMs.alpha = 0.4f
        binding.btnGoToDMs.text = "Go to DMs  ($totalSeconds)"

        setupButton()
        startCountdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }



    private fun setupButton() {
        binding.btnGoToDMs.setOnClickListener {
            // Disable immediately so user can't double-tap
            binding.btnGoToDMs.isEnabled = false
            redirectToDMs()
        }
    }

    private fun startCountdown() {
        countDownTimer = object : CountDownTimer(totalSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000L) + 1
                binding.tvTimer.text = secondsLeft.toString()
                binding.btnGoToDMs.text = "Go to DMs  ($secondsLeft)"
            }

            override fun onFinish() {
                binding.tvTimer.visibility = View.GONE
                binding.ivDone.visibility = View.VISIBLE
                binding.tvMessage.text = "You're good."
                binding.tvSubMessage.text = "DMs are ready whenever you are."
                binding.btnGoToDMs.isEnabled = true
                binding.btnGoToDMs.alpha = 1f
                binding.btnGoToDMs.text = "Go to DMs"
            }
        }.start()
    }

    /**
     * Attempts to click the Instagram DM tab via the live accessibility service.
     * Retries once after 500ms if the first attempt fails — this handles the case
     * where GLOBAL_ACTION_BACK was just performed and the home feed tree hasn't
     * fully settled yet.
     */
    private fun redirectToDMs() {
        // Dismiss the cooldown screen FIRST — instantly, no delay.
        // Instagram is already underneath (home feed after the back action).
        // Once this activity finishes, the DM click fires on the now-visible
        // Instagram window with a settled accessibility tree.
        finish()

        // Fire the DM tab click AFTER finish() — gives the window manager
        // a frame to tear down the overlay before we touch the tree.
        // 150ms is enough; the overlay dismiss is immediate from user's POV.
        Handler(Looper.getMainLooper()).postDelayed({
            attemptDMClick(retryCount = 0)
        }, 150L)
    }

    private fun attemptDMClick(retryCount: Int) {
        val service = EkagraAccessibilityService.instance ?: return

        val clicked = tryClickDMTab(service as EkagraAccessibilityService)

        if (clicked) {
            // Done — Instagram is navigating to DMs
            return
        }

        if (retryCount < 3) {
            // Tree not settled yet — retry every 400ms, up to 3 times (1.2s total)
            Handler(Looper.getMainLooper()).postDelayed({
                attemptDMClick(retryCount + 1)
            }, 400L)
        } else {
            // All retries exhausted — hard back as last resort
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    private fun tryClickDMTab(service: EkagraAccessibilityService): Boolean {
        val root = service.getRootNode() ?: return false

        // Verify we're actually looking at Instagram's tree
        if (root.packageName?.toString() != "com.instagram.android") return false

        val dmNodes = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/direct_tab"
        )

        if (dmNodes.isNullOrEmpty()) return false

        val node = dmNodes.firstOrNull() ?: return false

        // Extra guard: check the node is actually on screen and interactive
        // before clicking — prevents the "click succeeds but nothing happens" case
        if (!node.isVisibleToUser) return false

        return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
    }
}