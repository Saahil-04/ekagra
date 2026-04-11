package com.ekagra.app.onboarding

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ekagra.app.R
import com.google.android.material.button.MaterialButton

/**
 * OnboardingPagerAdapter
 *
 * RecyclerView.Adapter powering the ViewPager2 in OnboardingActivity.
 * 4 pages, each with its own layout and ViewHolder.
 *
 * The Activity communicates downward through:
 *   - [refreshPermissions] → updates the permissions slide in real-time
 *   - [OnboardingCallbacks] interface → Activity responds to user actions
 */
class OnboardingPagerAdapter(
    private val context: Context,
    private val callbacks: OnboardingCallbacks
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ── Permission state (kept in adapter so VH can read on bind) ──────────
    private var a11yGranted     = false
    private var overlayGranted  = false
    private var batteryGranted  = false

    // ── Exposed reference to permissions VH for live updates ───────────────
    private var permVH: PermissionsViewHolder? = null

    // ── Page count ─────────────────────────────────────────────────────────
    override fun getItemCount() = 4

    // ── View type == position ───────────────────────────────────────────────
    override fun getItemViewType(position: Int) = position

    // ── Interface ──────────────────────────────────────────────────────────

    interface OnboardingCallbacks {
        /** User tapped Next on a non-permissions slide. */
        fun onNextPage(fromPage: Int)
        /** Permissions state changed — activity should adjust swipe-lock. */
        fun onPermissionsStateChanged(allGranted: Boolean)
        /** User tapped the permission-specific "open settings" button. */
        fun onOpenPermissionSettings(permissionType: PermissionType)
        /** User tapped "Let's Start" on the final slide. */
        fun onOnboardingComplete()
    }

    enum class PermissionType { ACCESSIBILITY, OVERLAY, BATTERY }

    // ── Inflate ────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return when (viewType) {
            0 -> WelcomeViewHolder(inflater.inflate(R.layout.item_onboard_welcome, parent, false))
            1 -> HowItWorksViewHolder(inflater.inflate(R.layout.item_onboard_how, parent, false))
            2 -> PermissionsViewHolder(inflater.inflate(R.layout.item_onboard_permissions, parent, false))
            3 -> DoneViewHolder(inflater.inflate(R.layout.item_onboard_done, parent, false))
            else -> throw IllegalStateException("Unknown viewType $viewType")
        }
    }

    // ── Bind ───────────────────────────────────────────────────────────────

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is WelcomeViewHolder    -> holder.bind()
            is HowItWorksViewHolder -> holder.bind()
            is PermissionsViewHolder -> {
                permVH = holder
                holder.bind(a11yGranted, overlayGranted, batteryGranted)
            }
            is DoneViewHolder       -> holder.bind()
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Called from OnboardingActivity.onResume to push current permission state
     * into the permissions slide. Triggers a targeted notify so only page 2 redraws.
     */
    fun refreshPermissions(a11y: Boolean, overlay: Boolean, battery: Boolean) {
        a11yGranted    = a11y
        overlayGranted = overlay
        batteryGranted = battery

        // Fast path: if VH is attached, update it directly without full rebind
        permVH?.updateState(a11y, overlay, battery) ?: notifyItemChanged(2)

        // Notify activity so it can adjust the swipe-lock
        callbacks.onPermissionsStateChanged(a11y && overlay && battery)
    }

    // ── ViewHolders ────────────────────────────────────────────────────────

    /** Slide 0 — Welcome */
    inner class WelcomeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val btnGetStarted: MaterialButton = view.findViewById(R.id.btnGetStarted)
        private val tvTitle: TextView = view.findViewById(R.id.tvWelcomeTitle)
        private val tvTagline: TextView = view.findViewById(R.id.tvWelcomeTagline)

        fun bind() {
            animateElements(tvTitle, tvTagline, btnGetStarted)
            btnGetStarted.setOnClickListener { callbacks.onNextPage(0) }
        }
    }

    /** Slide 1 — How it works */
    inner class HowItWorksViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val btnNext: MaterialButton = view.findViewById(R.id.btnHowNext)
        private val icon1: View = view.findViewById(R.id.ivHowIcon1)
        private val icon2: View = view.findViewById(R.id.ivHowIcon2)
        private val text1: TextView = view.findViewById(R.id.tvHowDesc1)
        private val text2: TextView = view.findViewById(R.id.tvHowDesc2)

        fun bind() {
            animateElements(icon1, text1, icon2, text2, btnNext)
            btnNext.setOnClickListener { callbacks.onNextPage(1) }
        }
    }

    /** Slide 2 — Permissions (critical gating logic) */
    inner class PermissionsViewHolder(private val root: View) : RecyclerView.ViewHolder(root) {
        private val rowA11y: View           = root.findViewById(R.id.rowPermA11y)
        private val rowOverlay: View        = root.findViewById(R.id.rowPermOverlay)
        private val rowBattery: View        = root.findViewById(R.id.rowPermBattery)
        private val tvA11yStatus: TextView  = root.findViewById(R.id.tvPermA11yStatus)
        private val tvOverlayStatus: TextView = root.findViewById(R.id.tvPermOverlayStatus)
        private val tvBatteryStatus: TextView = root.findViewById(R.id.tvPermBatteryStatus)
        private val btnA11y: MaterialButton   = root.findViewById(R.id.btnGrantA11y)
        private val btnOverlay: MaterialButton = root.findViewById(R.id.btnGrantOverlay)
        private val btnBattery: MaterialButton = root.findViewById(R.id.btnGrantBattery)
        private val btnNext: MaterialButton   = root.findViewById(R.id.btnPermNext)

        fun bind(a11y: Boolean, overlay: Boolean, battery: Boolean) {
            animateElements(rowA11y, rowOverlay, rowBattery, btnNext)
            setupButtons()
            updateState(a11y, overlay, battery)
        }

        fun updateState(a11y: Boolean, overlay: Boolean, battery: Boolean) {
            applyPermRow(tvA11yStatus,    btnA11y,    a11y)
            applyPermRow(tvOverlayStatus, btnOverlay, overlay)
            applyPermRow(tvBatteryStatus, btnBattery, battery)

            val allDone = a11y && overlay && battery
            btnNext.isEnabled = allDone
            btnNext.alpha = if (allDone) 1f else 0.45f
        }

        private fun setupButtons() {
            btnA11y.setOnClickListener {
                callbacks.onOpenPermissionSettings(PermissionType.ACCESSIBILITY)
            }
            btnOverlay.setOnClickListener {
                callbacks.onOpenPermissionSettings(PermissionType.OVERLAY)
            }
            btnBattery.setOnClickListener {
                callbacks.onOpenPermissionSettings(PermissionType.BATTERY)
            }
            btnNext.setOnClickListener { callbacks.onNextPage(2) }
        }

        private fun applyPermRow(tv: TextView, btn: MaterialButton, granted: Boolean) {
            if (granted) {
                tv.text = "✔  Granted"
                tv.setTextColor(context.getColor(R.color.status_success))
                btn.visibility = View.GONE
            } else {
                tv.text = "✖  Not granted"
                tv.setTextColor(context.getColor(R.color.status_error))
                btn.visibility = View.VISIBLE
            }
        }
    }

    /** Slide 3 — Completion */
    inner class DoneViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val btnStart: MaterialButton = view.findViewById(R.id.btnLetsStart)
        private val tvTitle: TextView        = view.findViewById(R.id.tvDoneTitle)
        private val tvSub: TextView          = view.findViewById(R.id.tvDoneSub)

        fun bind() {
            animateElements(tvTitle, tvSub, btnStart)
            btnStart.setOnClickListener { callbacks.onOnboardingComplete() }
        }
    }

    // ── Animation helper ────────────────────────────────────────────────────

    /**
     * Staggers a fade+translateY entrance animation across the given views.
     * Each view starts invisible below its final position and animates up.
     */
    private fun animateElements(vararg views: View) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 48f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 90L) + 120L)
                .setDuration(420L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
                .start()
        }
    }
}
