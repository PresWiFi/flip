package com.blakeriding.flip

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Two jobs:
 * 1. The blocker: watches which app is in front and covers it with the lock screen when it's blocked,
 *    however it was opened (launcher, recents, a notification, a link). While a limited app stays open
 *    it re-checks every few seconds, so a limit or an unlock running out mid-use still locks it.
 * 2. The settings guard (while protection is on): backs out of system screens that could uninstall,
 *    force-stop or switch off Flip.
 */
class GuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var foreground: String? = null
    private var ignored: Set<String> = emptySet()
    private var lastToastAt = 0L
    private var lastLogAt = 0L
    private val tick = Runnable { check() }
    private val recheck = Runnable { if (Store.state.value.settings.protectionOn) guard() }

    override fun onServiceConnected() {
        Store.init(this)
        // Keyboards and the system UI (notification shade) open over an app without leaving it.
        val imes = try {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        } catch (_: Exception) {
            emptyList()
        }
        ignored = setOf("com.android.systemui") + imes
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Switched off in accessibility settings (or the app was stopped).
        if (Store.state.value.settings.protectionOn) Store.update { it.log(LogKind.GUARD_OFF, nowMs()) }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg != packageName && Store.state.value.settings.protectionOn && isSensitive(pkg)) guard()
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || pkg in ignored) return
        if (pkg != foreground) {
            foreground = pkg
            entered(pkg)
        }
        check()
    }

    /** Opening a limited app shows how much time is left today. */
    private fun entered(pkg: String) {
        if (pkg == packageName) return
        val v = verdictNow(this, pkg)
        if (v.blocked) return
        val text = when {
            v.grantUntil != null -> "Unlocked: ${fmtDuration(v.grantUntil - nowMs())} left"
            v.leftMs != null -> "${fmtDuration(v.leftMs)} left today"
            else -> return
        }
        toast(text)
    }

    private fun check() {
        handler.removeCallbacks(tick)
        val pkg = foreground ?: return
        if (pkg == packageName || !Store.state.value.hasRules(pkg)) return
        // Screen off: nothing is being used, check again when it's back on.
        if (!getSystemService(PowerManager::class.java).isInteractive) {
            handler.postDelayed(tick, TICK_MS)
            return
        }
        val v = verdictNow(this, pkg, maxAgeMs = 5_000)
        if (v.blocked) block(pkg) else handler.postDelayed(tick, TICK_MS)
    }

    /** Covers the app with the lock screen. Leaving the lock screen goes home, never back to the app. */
    private fun block(pkg: String) {
        foreground = null
        BlockActivity.start(this, pkg)
        NotifService.sweep()
    }

    /** Backs out of settings/installer screens that show Flip next to something that could switch it off. */
    private fun guard() {
        val label = getString(R.string.app_name)
        // Check every window, not just the active one: a confirm dialog doesn't name the app, the page under it does.
        val screen = windows.firstNotNullOfOrNull { window ->
            val root = window.root ?: return@firstNotNullOfOrNull null
            val pkg = root.packageName?.toString()
            if (!isSensitive(pkg)) return@firstNotNullOfOrNull null
            // Exact name only, so screens that merely contain the word "flip" (e.g. "Flip to mute") are left alone.
            val named = root.findAccessibilityNodeInfosByText(label).any {
                it.text?.toString()?.trim().equals(label, ignoreCase = true) ||
                    it.text?.toString()?.trim().equals("Use $label", ignoreCase = true)
            }
            pkg.takeIf { named && KEYWORDS.any { k -> root.findAccessibilityNodeInfosByText(k).isNotEmpty() } }
        } ?: return
        performGlobalAction(GLOBAL_ACTION_BACK)
        val now = SystemClock.elapsedRealtime()
        // One log entry per attempt, not one per back press.
        if (now - lastLogAt > LOG_INTERVAL_MS) Store.update { it.log(LogKind.BLOCKED_SCREEN, nowMs(), screen) }
        lastLogAt = now
        if (now - lastToastAt > TOAST_INTERVAL_MS) {
            lastToastAt = now
            toast(getString(R.string.protected_toast))
        }
        // Keep backing out until the screen is gone, even if no further event arrives.
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, RECHECK_MS)
    }

    private fun isSensitive(pkg: String?) = pkg != null && SENSITIVE.any { it in pkg }

    private companion object {
        const val TICK_MS = 10_000L
        const val RECHECK_MS = 300L
        const val TOAST_INTERVAL_MS = 3000L
        const val LOG_INTERVAL_MS = 30_000L

        // Settings apps, installers and OEM phone-manager apps (OnePlus/Oppo use oplus/coloros names).
        val SENSITIVE = listOf(
            "settings", "packageinstaller", "permissioncontroller", "securitycenter", "safecenter",
            "com.oplus.battery", "com.coloros", "com.android.vending",
        )

        // Words on the screens that can switch Flip off: app info, uninstall, default home app,
        // accessibility, device admin, usage access, notification access. Matched ignoring case.
        val KEYWORDS = listOf(
            "Force stop", "Uninstall", "Clear storage", "Clear data", "Disable", "Deactivate", "Turn off",
            "Home app", "Default home", "Default apps", "Accessibility", "admin app", "Device admin",
            "Usage access", "Usage data access", "Notification access", "Device & app notifications",
        )
    }
}
