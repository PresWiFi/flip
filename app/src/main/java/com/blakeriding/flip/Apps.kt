package com.blakeriding.flip

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.ContactsContract
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri

/** Today's foreground time per app, from Android's usage stats (needs usage access). */
object Usage {
    @Volatile private var cached: Pair<Long, Map<String, Long>>? = null

    /** Reuses a result up to [maxAgeMs] old, so frequent callers don't re-read the whole day. */
    fun today(context: Context, maxAgeMs: Long = 0): Map<String, Long> {
        val now = nowZ()
        cached?.let { (at, map) -> if (now.ms() - at <= maxAgeMs && dateOf(at, now.zone) == now.toLocalDate()) return map }
        if (!hasUsageAccess(context)) return emptyMap()
        val from = startOfDay(now)
        val events = ArrayList<UsageEv>()
        try {
            val q = context.getSystemService(UsageStatsManager::class.java).queryEvents(from, now.ms())
            val e = UsageEvents.Event()
            while (q.hasNextEvent()) {
                q.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> events += UsageEv(e.packageName, e.timeStamp, true)
                    UsageEvents.Event.ACTIVITY_PAUSED -> events += UsageEv(e.packageName, e.timeStamp, false)
                }
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        return foregroundTime(events, from, now.ms()).also { cached = now.ms() to it }
    }
}

/** Whether [pkg] is blocked right now, and why. */
fun verdictNow(context: Context, pkg: String, maxAgeMs: Long = 0): Verdict {
    val s = Store.state.value
    if (!s.hasRules(pkg)) return Verdict.FREE
    return s.verdict(pkg, Usage.today(context, maxAgeMs), nowZ())
}

data class AppEntry(val pkg: String, val label: String)

/** Installed apps you can open, their names and icons. */
object Apps {
    private val labels = HashMap<String, String>()
    private val icons = LruCache<String, ImageBitmap>(120)

    fun list(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != context.packageName }
            .distinctBy { it.first }
            .onEach { (p, l) -> synchronized(labels) { labels[p] = l } }
            .map { AppEntry(it.first, it.second) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String = synchronized(labels) {
        labels.getOrPut(pkg) {
            try {
                val pm = context.packageManager
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg.substringAfterLast('.')
            }
        }
    }

    /** The app's icon at [sizePx]. [pixel] shrinks it to 24px first, to be drawn back up as chunky pixels. */
    fun icon(context: Context, pkg: String, sizePx: Int, pixel: Boolean): ImageBitmap? {
        val size = if (pixel) 24 else sizePx
        val key = "$pkg@$size"
        icons.get(key)?.let { return it }
        return try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
                .also { icons.put(key, it) }
        } catch (_: Exception) {
            null
        }
    }

    fun launchIntent(context: Context, pkg: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Opens [pkg], or its lock screen if it's blocked. */
fun openApp(context: Context, pkg: String) {
    if (verdictNow(context, pkg).blocked) {
        BlockActivity.start(context, pkg)
        return
    }
    val intent = Apps.launchIntent(context, pkg) ?: return context.toast("Can't open that app")
    context.startActivity(intent)
}

fun dial(context: Context, number: String = "") {
    val uri = if (number.isEmpty()) "tel:" else "tel:" + android.net.Uri.encode(number)
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        context.toast("No phone app")
    }
}

data class Contact(val name: String, val number: String)

/** Contacts with a phone number, for T9 search. Empty without contacts permission. */
fun loadContacts(context: Context): List<Contact> {
    if (!hasContacts(context)) return emptyList()
    val out = ArrayList<Contact>()
    try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                out += Contact(name, number)
            }
        }
    } catch (_: Exception) {
    }
    return out.distinctBy { it.name to it.number.filter(Char::isDigit) }
}
