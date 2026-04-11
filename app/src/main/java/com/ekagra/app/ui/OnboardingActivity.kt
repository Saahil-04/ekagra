package com.ekagra.app.ui



import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.ekagra.app.R
import com.ekagra.app.databinding.ActivityOnboardingBinding
import com.ekagra.app.onboarding.OnboardingPagerAdapter
import com.ekagra.app.utils.AccessibilityUtils
import com.ekagra.app.utils.PreferencesManager

/**
 * OnboardingActivity
 *
 * Shown ONLY on first install (guarded by [PreferencesManager.isOnboardingComplete]).
 * After completion it sets the flag and launches [MainActivity].
 *
 * Architecture:
 *  - ViewPager2 with [OnboardingPagerAdapter] (4 pages)
 *  - Animated dot indicators drawn programmatically
 *  - Forward swipe disabled on the Permissions page until all 3 are granted
 *  - Each page uses staggered element animations via the adapter
 */
class OnboardingActivity : AppCompatActivity(), OnboardingPagerAdapter.OnboardingCallbacks {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingPagerAdapter

    private val dotViews = mutableListOf<View>()
    private val DOT_SIZE_DP     = 8
    private val DOT_ACTIVE_W_DP = 24
    private val DOT_MARGIN_DP   = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupViewPager()
        setupDots()
    }

    override fun onResume() {
        super.onResume()
        // Refresh permissions — user may have come back from system settings
        refreshPermissions()
    }

    // ── Edge-to-edge ────────────────────────────────────────────────────────

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    // ── ViewPager2 ──────────────────────────────────────────────────────────

    private fun setupViewPager() {
        adapter = OnboardingPagerAdapter(this, this)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 1

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                animateDots(position)

                // Lock swipe forward on permissions page unless all granted
                // (isUserInputEnabled controls ALL swipes; we re-enable in refreshPermissions)
                if (position == 2) {
                    val a11y = AccessibilityUtils.isAccessibilityServiceEnabled(this@OnboardingActivity)
                    val overlay = Settings.canDrawOverlays(this@OnboardingActivity)
                    val battery = isBatteryOptimizationDisabled()
                    binding.viewPager.isUserInputEnabled = a11y && overlay && battery
                } else {
                    binding.viewPager.isUserInputEnabled = true
                }
            }
        })
    }

    // ── Dots ────────────────────────────────────────────────────────────────

    private fun setupDots() {
        val density = resources.displayMetrics.density
        val dotSizePx    = (DOT_SIZE_DP * density).toInt()
        val dotMarginPx  = (DOT_MARGIN_DP * density).toInt()
        val activeDotPx  = (DOT_ACTIVE_W_DP * density).toInt()

        repeat(adapter.itemCount) { i ->
            val dot = View(this).apply {
                val params = LinearLayout.LayoutParams(
                    if (i == 0) activeDotPx else dotSizePx,
                    dotSizePx
                ).apply { setMargins(dotMarginPx, 0, dotMarginPx, 0) }
                layoutParams = params
                setBackgroundResource(
                    if (i == 0) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive
                )
            }
            binding.dotsContainer.addView(dot)
            dotViews.add(dot)
        }
    }

    private fun animateDots(selectedPosition: Int) {
        val density      = resources.displayMetrics.density
        val dotSizePx    = (DOT_SIZE_DP * density).toInt()
        val activeDotPx  = (DOT_ACTIVE_W_DP * density).toInt()

        dotViews.forEachIndexed { index, dot ->
            val targetW  = if (index == selectedPosition) activeDotPx else dotSizePx
            val targetBg = if (index == selectedPosition) R.drawable.bg_dot_active
            else R.drawable.bg_dot_inactive

            dot.setBackgroundResource(targetBg)

            // Animate width change
            val params = dot.layoutParams
            android.animation.ValueAnimator.ofInt(dot.width, targetW).apply {
                duration = 260L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    params.width = it.animatedValue as Int
                    dot.layoutParams = params
                }
                start()
            }
        }
    }

    // ── Permission helpers ───────────────────────────────────────────────────

    private fun isBatteryOptimizationDisabled(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun refreshPermissions() {
        val a11y    = AccessibilityUtils.isAccessibilityServiceEnabled(this)
        val overlay = Settings.canDrawOverlays(this)
        val battery = isBatteryOptimizationDisabled()
        adapter.refreshPermissions(a11y, overlay, battery)
    }

    // ── OnboardingCallbacks ─────────────────────────────────────────────────

    override fun onNextPage(fromPage: Int) {
        val next = fromPage + 1
        if (next < adapter.itemCount) {
            binding.viewPager.currentItem = next
        }
    }

    override fun onPermissionsStateChanged(allGranted: Boolean) {
        // If user just granted all permissions while on page 2, unlock swipe
        if (binding.viewPager.currentItem == 2) {
            binding.viewPager.isUserInputEnabled = allGranted
        }
    }

    override fun onOpenPermissionSettings(permissionType: OnboardingPagerAdapter.PermissionType) {
        val intent = when (permissionType) {
            OnboardingPagerAdapter.PermissionType.ACCESSIBILITY -> {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            OnboardingPagerAdapter.PermissionType.OVERLAY -> {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            }
            OnboardingPagerAdapter.PermissionType.BATTERY -> {
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onOnboardingComplete() {
        // Persist: user will never see onboarding again
        PreferencesManager.markOnboardingComplete(this)

        // Navigate to main screen with a fade transition
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
