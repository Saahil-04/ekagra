package com.ekagra.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ekagra.app.ui.CooldownActivity
import com.ekagra.app.utils.PreferencesManager
import java.lang.ref.WeakReference

class EkagraAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "EkagraA11y"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"

        // ── Tab / view IDs ───────────────────────────────────────────────────────
        private const val ID_REELS_TAB   = "com.instagram.android:id/clips_tab"
        private const val ID_EXPLORE_TAB = "com.instagram.android:id/search_tab"
        private const val ID_DM_TAB      = "com.instagram.android:id/direct_tab"
        private const val ID_HOME_TAB    = "com.instagram.android:id/feed_tab"
        private const val ID_PROFILE_TAB = "com.instagram.android:id/profile_tab"
        private const val ID_REEL_VIDEO  = "com.instagram.android:id/clips_video_container"

        // ── Share sheet detection ────────────────────────────────────────────────

        private val SHARE_SHEET_VIEW_IDS = listOf(
            "com.instagram.android:id/direct_private_share_container_view",
            "com.instagram.android:id/share_to_container",
            "com.instagram.android:id/direct_share_sheet",
            "com.instagram.android:id/reshare_bottom_sheet",
            "com.instagram.android:id/share_sheet_recipient_list",
            "com.instagram.android:id/row_thread_composer_edittext",
            "com.instagram.android:id/recipient_chooser_row",
            "com.instagram.android:id/send_button"
        )

        // Header text that appears only on the share sheet; case-insensitive.
        private val SHARE_SHEET_TEXT_SIGNALS = listOf("send to", "share to")

        //  Timing constants ─────────────────────────────────────────────────────
        private const val SCROLL_GRACE_MS             = 800L
        private const val POST_REDIRECT_IGNORE_MS     = 2500L
        private const val CONTENT_CHANGE_THROTTLE_MS  = 300L
        private const val COOLDOWN_THROTTLE_MS        = 3000L

        // ── Weak service reference ───────────────────────────────────────────────
        // WeakReference prevents the static field from keeping the service alive
        // past its natural lifecycle.  CooldownActivity resolves this on demand;
        // if the service is gone, the reference returns null and the caller skips.
        @Volatile private var weakInstance: WeakReference<EkagraAccessibilityService>? = null

        /** Returns the live service, or null if it has been destroyed. */
        val instance: EkagraAccessibilityService?
            get() = weakInstance?.get()
    }

    // ── Instance state ───────────────────────────────────────────────────────────
    private var reelEntryTimeMs      = 0L
    private var lastRedirectTimeMs   = 0L
    private var lastContentChangeMs  = 0L
    private var lastCooldownLaunch   = 0L
    private var currentWindowPackage = ""

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cooldownRunnable = Runnable { launchCooldown() }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        weakInstance = WeakReference(this)
        serviceInfo = serviceInfo.apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED  or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType      = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags =
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.i(TAG, "Ekagra connected ✓")
    }

    override fun onDestroy() {

        mainHandler.removeCallbacks(cooldownRunnable)
        weakInstance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    // ── Event dispatch ───────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!PreferencesManager.isFocusModeEnabled(this)) return
        if (isPostRedirectIgnoreActive()) return

        // Throttle rapid-fire CONTENT_CHANGED events — they fire on every minor
        // DOM mutation during transitions and produce stale rootInActiveWindow reads.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            if (now - lastContentChangeMs < CONTENT_CHANGE_THROTTLE_MS) return
            lastContentChangeMs = now
        }

        val eventPackage = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentWindowPackage = eventPackage
        }

        if (eventPackage != INSTAGRAM_PACKAGE) return

        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return


        if (root.packageName?.toString() != INSTAGRAM_PACKAGE) {
            Log.d(TAG, "rootInActiveWindow is stale (${root.packageName}) — skipping")
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScroll(root)
            else                                  -> handleWindowChange(root)
        }
    }

    // ── Window-change logic ────────────────────────────────

    private fun handleWindowChange(root: AccessibilityNodeInfo) {

        if (isShareSheetVisible(root)) {
            Log.d(TAG, "Window change ignored — share sheet overlay")
            return
        }

        if (isTabSelected(root, ID_EXPLORE_TAB)) {
            Log.i(TAG, "🚫 Explore — blocking")
            redirect(root)
            return
        }

        if (isTabSelected(root, ID_REELS_TAB) && !isReelVideoOnScreen(root)) {
            Log.i(TAG, "🚫 Reels tab direct tap — blocking")
            redirect(root)
            return
        }

        val reelOnScreen    = isReelVideoOnScreen(root)
        val reelTabSelected = isTabSelected(root, ID_REELS_TAB)

        when {
            reelOnScreen && reelTabSelected -> {
                Log.i(TAG, "🚫 Reel fullscreen + reels tab — blocking immediately")
                redirect(root)
            }
            reelOnScreen && reelEntryTimeMs == 0L -> {
                reelEntryTimeMs = System.currentTimeMillis()
                Log.i(TAG, "▶ Reel entered via non-tab path — grace started")
            }
            !reelOnScreen && reelEntryTimeMs != 0L -> {
                Log.i(TAG, "✓ Left reel — reset")
                reelEntryTimeMs = 0L
            }
        }
    }

    // Scroll logic ─────────────────────────────────────────────────────────────

    private fun handleScroll(root: AccessibilityNodeInfo) {
        if (reelEntryTimeMs == 0L) return

        // Share-sheet guard: scroll originated inside the overlay, not the reel.
        if (isShareSheetVisible(root)) {
            Log.d(TAG, "Scroll ignored — share sheet is open")
            return
        }

        val elapsed = System.currentTimeMillis() - reelEntryTimeMs
        if (elapsed < SCROLL_GRACE_MS) {
            Log.d(TAG, "Scroll ignored — grace (${elapsed}ms)")
            return
        }

        if (!isReelVideoOnScreen(root)) {
            Log.d(TAG, "Scroll — reel gone, reset")
            reelEntryTimeMs = 0L
            return
        }

        Log.i(TAG, "📜 Scrolled past reel (${elapsed}ms) — blocking")
        redirect(root)
    }

    // ── Share-sheet detection ─────────────────────────────────────────────────────
    /**
     * Pass 1 — ID lookup (O(1) framework index, primary signal).
     * Pass 2 — Shallow text scan ≤ 3 levels deep (fallback for renamed IDs).
     */
    private fun isShareSheetVisible(root: AccessibilityNodeInfo): Boolean {
        for (viewId in SHARE_SHEET_VIEW_IDS) {
            val nodes: List<AccessibilityNodeInfo>? =
                root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                Log.d(TAG, "Share sheet — viewId: $viewId")
                return true
            }
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (containsShareSheetText(child, depthRemaining = 2)) {
                Log.d(TAG, "Share sheet — text signal in child[$i]")
                return true
            }
        }
        return false
    }

    private fun containsShareSheetText(node: AccessibilityNodeInfo, depthRemaining: Int): Boolean {
        if (matchesShareSheetText(node)) return true
        if (depthRemaining <= 0) return false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsShareSheetText(child, depthRemaining - 1)) return true
        }
        return false
    }

    private fun matchesShareSheetText(node: AccessibilityNodeInfo): Boolean {
        val text: String = node.text?.toString()?.lowercase() ?: ""
        val cd:   String = node.contentDescription?.toString()?.lowercase() ?: ""
        return SHARE_SHEET_TEXT_SIGNALS.any { signal -> text.contains(signal) || cd.contains(signal) }
    }

    // ── Navigation helpers ───────────────────────────────────────────────────────

    private fun isReelVideoOnScreen(root: AccessibilityNodeInfo): Boolean {
        if (!isNodePresent(root, ID_REEL_VIDEO)) return false
        if (isTabSelected(root, ID_HOME_TAB))    return false
        if (isTabSelected(root, ID_PROFILE_TAB)) return false
        if (isTabSelected(root, ID_DM_TAB))      return false
        return true
    }

    private fun isTabSelected(root: AccessibilityNodeInfo, tabId: String): Boolean {
        val nodes: List<AccessibilityNodeInfo>? =
            root.findAccessibilityNodeInfosByViewId(tabId)
        if (nodes.isNullOrEmpty()) return false
        for (node in nodes) {
            if (node.isSelected) return true
            for (i in 0 until node.childCount) {
                if (node.getChild(i)?.isSelected == true) return true
            }
        }
        return false
    }

    private fun isNodePresent(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes: List<AccessibilityNodeInfo>? =
            root.findAccessibilityNodeInfosByViewId(viewId)
        return !nodes.isNullOrEmpty()
    }

    // ── Redirect ─────────────────────────────────────────────────────────────────

    private fun redirect(root: AccessibilityNodeInfo) {
        reelEntryTimeMs  = 0L
        lastRedirectTimeMs = System.currentTimeMillis()

        // Cancel any previously scheduled cooldown before scheduling a new one,
        // preventing double-fires when redirect() is called in quick succession.
        mainHandler.removeCallbacks(cooldownRunnable)

        val dmClicked = clickTab(root, ID_DM_TAB)
        if (dmClicked) {
            mainHandler.postDelayed(cooldownRunnable, 300L)
            return
        }

        val homeClicked = clickTab(root, ID_HOME_TAB)
        if (!homeClicked) performGlobalAction(GLOBAL_ACTION_BACK)

        mainHandler.postDelayed(cooldownRunnable, 600L)
    }

    private fun launchCooldown() {
        val now = System.currentTimeMillis()
        if (now - lastCooldownLaunch < COOLDOWN_THROTTLE_MS) return
        lastCooldownLaunch = now
        startActivity(
            Intent(this, CooldownActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun clickTab(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes: List<AccessibilityNodeInfo>? =
            root.findAccessibilityNodeInfosByViewId(viewId)
        val clicked = nodes?.firstOrNull()
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        if (clicked) Log.i(TAG, "✓ Redirected to: $viewId")
        return clicked
    }

    // ── Post-redirect silence ─────────────────────────────────────────────────────

    private fun isPostRedirectIgnoreActive(): Boolean {
        if (lastRedirectTimeMs == 0L) return false
        val elapsed = System.currentTimeMillis() - lastRedirectTimeMs
        if (elapsed < POST_REDIRECT_IGNORE_MS) {
            Log.d(TAG, "Post-redirect silence (${elapsed}ms)")
            return true
        }
        return false
    }

    // ── CooldownActivity bridge ───────────────────────────────────────────────────
    fun getRootNode(): AccessibilityNodeInfo? = rootInActiveWindow
}