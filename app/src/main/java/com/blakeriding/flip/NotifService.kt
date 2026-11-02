package com.blakeriding.flip

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Clears notifications from blocked apps that are set to hide them. Needs notification access. */
class NotifService : NotificationListenerService() {
    override fun onListenerConnected() {
        Store.init(this)
        instance = this
        sweepAll()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (shouldHide(sbn.packageName)) cancel(sbn)
    }

    private fun shouldHide(pkg: String): Boolean {
        val rule = Store.state.value.ruleFor(pkg) ?: return false
        return rule.hideNotifs && verdictNow(this, pkg, maxAgeMs = 30_000).blocked
    }

    private fun cancel(sbn: StatusBarNotification) {
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {
        }
    }

    private fun sweepAll() {
        val active = try {
            activeNotifications
        } catch (_: Exception) {
            null
        } ?: return
        active.filter { shouldHide(it.packageName) }.forEach(::cancel)
    }

    companion object {
        @Volatile private var instance: NotifService? = null

        /** Clears notifications of apps that just became blocked. */
        fun sweep() {
            instance?.sweepAll()
        }
    }
}
