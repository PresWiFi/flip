package com.blakeriding.flip

import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class App : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        scope.launch { checkExits(this@App) }
    }
}

/** Android remembers why the app last stopped. A force-stop from Settings goes in the log. */
private fun checkExits(context: Context) {
    if (Build.VERSION.SDK_INT < 30) return
    val since = Store.state.value.exitsCheckedAt
    val exits = try {
        context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName, 0, 20)
    } catch (_: Exception) {
        return
    }.filter { it.timestamp > since }
    if (exits.isEmpty()) return
    // First run: start counting from now rather than logging old history.
    if (since == 0L) {
        Store.update { it.copy(exitsCheckedAt = exits.maxOf { e -> e.timestamp }) }
        return
    }
    Store.update { st ->
        var s = st
        for (e in exits.sortedBy { it.timestamp }) {
            // Updating the app also stops it; that's not tampering.
            val update = UPDATE_WORDS.any { it in e.description.orEmpty().lowercase() }
            if (e.reason == ApplicationExitInfo.REASON_USER_REQUESTED && !update) {
                s = s.log(LogKind.FORCE_STOPPED, e.timestamp, e.description.orEmpty().take(80))
            }
        }
        s.copy(exitsCheckedAt = exits.maxOf { it.timestamp })
    }
}

private val UPDATE_WORDS = listOf("update", "replace", "install")

/** While active, Android refuses to uninstall Flip. Turning it off is guarded by [GuardService]. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Store.init(context)
        Store.update { it.log(LogKind.ADMIN_OFF_ATTEMPT, nowMs()) }
        return context.getString(R.string.admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Store.init(context)
        Store.update { it.log(LogKind.ADMIN_OFF, nowMs()) }
    }
}

fun zone(): ZoneId = ZoneId.systemDefault()
fun nowMs(): Long = System.currentTimeMillis()
fun nowZ(): ZonedDateTime = ZonedDateTime.now(zone())
fun newId(): String = UUID.randomUUID().toString()

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

// ---------- Setup checks ----------

fun adminComponent(context: Context) = ComponentName(context, AdminReceiver::class.java)

fun isAdminActive(context: Context): Boolean =
    context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(context))

fun adminIntent(context: Context): Intent =
    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_description))

fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val me = ComponentName(context, GuardService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}

fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

fun hasNotifAccess(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

fun notifAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

fun isDefaultHome(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
}

fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

fun hasContacts(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

fun ignoresBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@android.annotation.SuppressLint("BatteryLife") // Sideloaded, and the blocker has to stay running.
fun batteryIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

fun hasNfc(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context) != null
fun nfcOn(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

// ---------- NFC ----------

fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

/** 4-byte IDs starting 0x08 are random on every tap (ISO 14443-3), so they can't identify a card. */
fun isRandomUid(uid: String) = uid.length == 8 && uid.startsWith("08")

private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or
    NfcAdapter.FLAG_READER_NFC_B or
    NfcAdapter.FLAG_READER_NFC_F or
    NfcAdapter.FLAG_READER_NFC_V or
    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

/** Reads the UID of any card tapped while this activity is in the foreground. Call from onResume. */
fun Activity.startCardReader(onUid: (String) -> Unit) {
    NfcAdapter.getDefaultAdapter(this)?.enableReaderMode(this, { tag -> runOnUiThread { onUid(tag.id.toHex()) } }, READER_FLAGS, null)
}

fun Activity.stopCardReader() {
    NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
}
