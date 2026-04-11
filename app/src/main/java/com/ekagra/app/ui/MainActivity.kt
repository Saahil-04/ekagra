package com.ekagra.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.ekagra.app.utils.AccessibilityUtils
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ekagra.app.R
import com.ekagra.app.databinding.ActivityMainBinding
import com.ekagra.app.utils.PreferencesManager
import com.google.android.material.snackbar.Snackbar
import com.ekagra.app.utils.ThemeMode
import com.ekagra.app.service.EkagraForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PreferencesManager.isOnboardingComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        applySavedTheme()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupSettingsButton()   // ← replaces setupThemeToggle()
        setupClickListeners()
        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
        updateAccessibilityWarning()
        updateToggleState()
    }

    // ── Theme ────────────────────────────────────────────────────────────────

    private fun applySavedTheme() {
        AppCompatDelegate.setDefaultNightMode(
            PreferencesManager.getThemeMode(this).toNightMode()
        )
    }

    // ── Settings navigation (replaces toggle icon) ───────────────────────────

    private fun setupSettingsButton() {
        binding.btnSettings.setIconResource(R.drawable.settings_24)
        binding.btnSettings.contentDescription = getString(R.string.cd_settings)
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ── Window ───────────────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.R)
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val isLight = PreferencesManager.getThemeMode(this) != ThemeMode.DARK
        WindowInsetsControllerCompat(window, binding.root).apply {
            isAppearanceLightStatusBars    = isLight
            isAppearanceLightNavigationBars = isLight
        }
    }

    // ── Entrance animation ───────────────────────────────────────────────────

    private fun animateEntrance() {
        listOf(binding.layoutTopBar, binding.cardToggle, binding.tvBlockingStatus)
            .forEachIndexed { i, view ->
                view.alpha = 0f
                view.translationY = 40f
                view.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay(i * 80L + 60L)
                    .setDuration(380L)
                    .setInterpolator(DecelerateInterpolator(2f))
                    .start()
            }
    }

    // ── Click listeners ──────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.switchFocusMode.setOnCheckedChangeListener { _, isChecked ->
            onFocusModeToggled(isChecked)
        }
    }

    // ── State ────────────────────────────────────────────────────────────────

    private fun updateAccessibilityWarning() {
        val isEnabled = AccessibilityUtils.isAccessibilityServiceEnabled(this)
        binding.switchFocusMode.isEnabled = isEnabled
        if (!isEnabled) {
            binding.tvAccessibilityWarning.visibility = View.VISIBLE
            binding.tvAccessibilityWarning.setOnClickListener {
                AccessibilityUtils.openAccessibilitySettings(this)
            }
            binding.switchFocusMode.isChecked = false
            PreferencesManager.setFocusModeEnabled(this, false)
        } else {
            binding.tvAccessibilityWarning.visibility = View.GONE
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Snackbar.make(binding.root, "Allow 'Display over other apps' for the block screen to work", Snackbar.LENGTH_LONG)
                .setAction("Grant") {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }.show()
        }
    }

    private fun updateToggleState() {
        val isEnabled = PreferencesManager.isFocusModeEnabled(this)
        binding.switchFocusMode.setOnCheckedChangeListener(null)
        binding.switchFocusMode.isChecked = isEnabled
        binding.switchFocusMode.setOnCheckedChangeListener { _, checked -> onFocusModeToggled(checked) }
        updateBlockingStatus(isEnabled)
    }

    private fun updateBlockingStatus(isActive: Boolean) {
        binding.tvBlockingStatus.text =
            getString(if (isActive) R.string.blocking_status_active else R.string.blocking_status_inactive)
        binding.tvBlockingStatus.setTextColor(
            getColor(if (isActive) R.color.status_success else R.color.neutral_40)
        )
    }

    private fun onFocusModeToggled(isEnabled: Boolean) {
        if (isEnabled && !AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
            binding.switchFocusMode.isChecked = false
            showSnackbar(getString(R.string.snack_a11y_required), getString(R.string.snack_action_settings)) {
                AccessibilityUtils.openAccessibilitySettings(this)
            }
            return
        }
        PreferencesManager.setFocusModeEnabled(this, isEnabled)
        if (isEnabled) { EkagraForegroundService.start(this); showSnackbar(getString(R.string.snack_focus_on)) }
        else           { EkagraForegroundService.stop(this);  showSnackbar(getString(R.string.snack_focus_off)) }
        updateBlockingStatus(isEnabled)
    }

    private fun showSnackbar(message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        if (actionLabel != null && action != null) { snack.setAction(actionLabel) { action() }; snack.duration = Snackbar.LENGTH_LONG }
        snack.show()
    }
}