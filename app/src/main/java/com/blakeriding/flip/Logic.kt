package com.blakeriding.flip

import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

// Pure logic: no Android, no wall clock. Everything takes "now" so it can be tested.

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MIN = 24 * 60

fun dateOf(epoch: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
fun startOfDay(now: ZonedDateTime): Long = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
fun ZonedDateTime.ms(): Long = toInstant().toEpochMilli()

// ---------- Schedules ----------

/** Length in minutes. A window whose end isn't after its start runs past midnight; start == end is a full 24h. */
private fun Window.lengthMin(): Int = if (endMin > startMin) endMin - startMin else endMin + DAY_MIN - startMin

/** When this window ends, if it's active at [now]; null if it isn't. */
fun Window.activeUntil(now: ZonedDateTime): ZonedDateTime? {
    // A window can only be active if it started today or yesterday.
    for (back in 0L..1L) {
        val day = now.toLocalDate().minusDays(back)
        if (day.dayOfWeek.value !in days) continue
        val start = day.atStartOfDay(now.zone).plusMinutes(startMin.toLong())
        val end = start.plusMinutes(lengthMin().toLong())
        if (!now.isBefore(start) && now.isBefore(end)) return end
    }
    return null
}

/** True if [this] blocks at least everything [other] blocks. */
fun Window.covers(other: Window): Boolean {
    if (!days.containsAll(other.days)) return false
    val s = startMin
    val e = s + lengthMin()
    // Compare on the same day, and also with [other] shifted a day later (it may start after midnight inside [this]).
    return listOf(0, DAY_MIN).any { shift ->
        val os = other.startMin + shift
        os >= s && os + other.lengthMin() <= e
    }
}

// ---------- Usage ----------

/** One foreground change from Android's usage stats. */
data class UsageEv(val pkg: String, val at: Long, val resumed: Boolean)

/**
 * Foreground time per app from [from] until [now], from activity resumed/paused events in time order.
 * Only one app is in front at a time. An app whose first event is a pause was already open at [from].
 */
fun foregroundTime(events: List<UsageEv>, from: Long, now: Long): Map<String, Long> {
    val total = HashMap<String, Long>()
    val seen = HashSet<String>()
    var current: String? = null
    var since = from
    fun close(at: Long) {
        val pkg = current ?: return
        total[pkg] = (total[pkg] ?: 0L) + (at - since).coerceAtLeast(0)
        current = null
    }
    for (e in events.sortedBy { it.at }) {
        if (e.at < from || e.at > now) continue
        val first = seen.add(e.pkg)
        if (e.resumed) {
            if (current != e.pkg) {
                close(e.at)
                current = e.pkg
                since = e.at
            }
        } else if (current == e.pkg) {
            close(e.at)
        } else if (first && current == null) {
            // Open since before the start of the day.
            total[e.pkg] = (total[e.pkg] ?: 0L) + (e.at - from)
        }
    }
    close(now)
    return total
}

// ---------- Blocking ----------

enum class Reason { ALWAYS, SCHEDULE, LIMIT, GROUP }

data class Verdict(
    val blocked: Boolean,
    val reason: Reason? = null,
    /** When the block ends on its own (end of schedule, midnight), or null for "always". */
    val blockedUntil: Long? = null,
    /** Time left today before a limit kicks in, if the app has one. */
    val leftMs: Long? = null,
    /** Unlocked until this time. */
    val grantUntil: Long? = null,
    val groupName: String? = null,
) {
    companion object {
        val FREE = Verdict(blocked = false)
    }
}

fun AppState.ruleFor(pkg: String): AppRule? = rules.find { it.pkg == pkg }
fun AppState.groupsFor(pkg: String): List<Group> = groups.filter { pkg in it.pkgs }
fun AppState.hasRules(pkg: String): Boolean = ruleFor(pkg) != null || groupsFor(pkg).isNotEmpty()
fun AppState.activeGrant(pkg: String, now: Long): Grant? = grants.filter { it.pkg == pkg && it.until > now }.maxByOrNull { it.until }

/** Whether [pkg] may be used right now. [usage] is today's foreground time per app. */
fun AppState.verdict(pkg: String, usage: Map<String, Long>, now: ZonedDateTime): Verdict {
    val rule = ruleFor(pkg)
    val groups = groupsFor(pkg)
    if (rule == null && groups.isEmpty()) return Verdict.FREE
    val nowMs = now.ms()
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).ms()

    // Time left before a limit, for display (and to know when to re-check).
    val appLeft = rule?.dailyLimitMin?.let { it * MINUTE_MS - (usage[pkg] ?: 0L) }
    val groupLeft = groups.map { g -> g to g.limitMin * MINUTE_MS - g.pkgs.sumOf { usage[it] ?: 0L } }
    val left = (listOfNotNull(appLeft) + groupLeft.map { it.second }).minOrNull()?.coerceAtLeast(0)

    activeGrant(pkg, nowMs)?.let { return Verdict(false, leftMs = left, grantUntil = it.until) }

    if (rule != null) {
        if (rule.always) return Verdict(true, Reason.ALWAYS)
        rule.schedules.mapNotNull { it.activeUntil(now) }.maxOrNull()?.let {
            return Verdict(true, Reason.SCHEDULE, blockedUntil = it.ms())
        }
        if (appLeft != null && appLeft <= 0) return Verdict(true, Reason.LIMIT, blockedUntil = midnight, leftMs = 0)
    }
    groupLeft.firstOrNull { it.second <= 0 }?.let { (g, _) ->
        return Verdict(true, Reason.GROUP, blockedUntil = midnight, leftMs = 0, groupName = g.name)
    }
    return Verdict(false, leftMs = left)
}

// ---------- Unlocking ----------

fun AppState.quickUsedToday(now: ZonedDateTime): Int =
    log.count { it.kind == LogKind.UNLOCK_QUICK && dateOf(it.at, now.zone) == now.toLocalDate() }

fun AppState.quickLeft(now: ZonedDateTime): Int = (settings.quickPerDay - quickUsedToday(now)).coerceAtLeast(0)

val UnlockMethod.logKind: LogKind
    get() = when (this) {
        UnlockMethod.QUICK -> LogKind.UNLOCK_QUICK
        UnlockMethod.TAG -> LogKind.UNLOCK_TAG
        UnlockMethod.CODE -> LogKind.UNLOCK_CODE
        UnlockMethod.EMERGENCY -> LogKind.UNLOCK_EMERGENCY
    }

/** Unlocks [pkg] for the quick or normal unlock time. Returns null if no quick unlocks are left. */
fun AppState.unlock(pkg: String, method: UnlockMethod, now: ZonedDateTime, label: String = pkg): AppState? {
    if (method == UnlockMethod.QUICK && quickLeft(now) <= 0) return null
    val minutes = if (method == UnlockMethod.QUICK) settings.quickMin else settings.unlockMin
    val nowMs = now.ms()
    return copy(grants = grants.filter { it.until > nowMs && it.pkg != pkg } + Grant(pkg, nowMs + minutes * MINUTE_MS, method))
        .log(method.logKind, nowMs, label)
}

fun AppState.log(kind: LogKind, now: Long, detail: String = ""): AppState =
    copy(log = (log + LogEvent(now, kind, detail)).takeLast(1000))

/** Things that count as trying to get around Flip, as opposed to allowed unlocks. */
val LogKind.isAttempt: Boolean
    get() = this in setOf(
        LogKind.BLOCKED_SCREEN, LogKind.WRONG_CODE, LogKind.PROTECTION_OFF, LogKind.ADMIN_OFF_ATTEMPT,
        LogKind.ADMIN_OFF, LogKind.GUARD_OFF, LogKind.FORCE_STOPPED, LogKind.UNLOCK_EMERGENCY,
    )

// ---------- Loosening (needs the typed code while protection is on) ----------

/** Every way [new] is easier to get around than [old]. Empty = only tightening or neutral changes. */
fun loosenings(old: AppState, new: AppState, label: (String) -> String = { it }): List<String> {
    val out = mutableListOf<String>()
    for (o in old.rules) {
        val n = new.ruleFor(o.pkg)
        val name = label(o.pkg)
        if (n == null) {
            out += "Remove the rules for $name"
            continue
        }
        if (o.always && !n.always) out += "Stop always blocking $name"
        val ol = o.dailyLimitMin
        val nl = n.dailyLimitMin
        if (ol != null && (nl == null || nl > ol)) out += "Raise the daily limit for $name"
        if (o.schedules.any { ow -> n.schedules.none { it.covers(ow) } }) out += "Shorten the blocked hours for $name"
        if (o.hideNotifs && !n.hideNotifs) out += "Show $name notifications while blocked"
    }
    for (o in old.groups) {
        val n = new.groups.find { it.id == o.id }
        when {
            n == null -> out += "Remove the group ${o.name}"
            n.limitMin > o.limitMin -> out += "Raise the limit for ${o.name}"
            !n.pkgs.containsAll(o.pkgs) -> out += "Take apps out of ${o.name}"
        }
    }
    if (new.tags.any { t -> old.tags.none { it.id == t.id } }) out += "Add an unlock tag"
    val os = old.settings
    val ns = new.settings
    if (ns.quickPerDay > os.quickPerDay) out += "More quick unlocks per day"
    if (ns.quickMin > os.quickMin) out += "Longer quick unlocks"
    if (ns.unlockMin > os.unlockMin) out += "Longer unlocks"
    if (os.protectionOn && !ns.protectionOn) out += "Turn protection off"
    return out
}

// ---------- T9 search ----------

private const val T9_LETTERS = "abcdefghijklmnopqrstuvwxyz"
private const val T9_DIGITS = "22233344455566677778889999"

private fun plain(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** "Insta 2" -> "467828". Letters become their keypad digit, digits stay, everything else is dropped. */
fun t9(s: String): String = buildString {
    for (c in plain(s)) {
        val i = T9_LETTERS.indexOf(c)
        when {
            i >= 0 -> append(T9_DIGITS[i])
            c in '0'..'9' -> append(c)
        }
    }
}

/** 0 = whole name starts with the keys, 1 = a later word does, null = no match. Lower ranks first. */
fun t9Rank(digits: String, name: String): Int? {
    if (digits.isEmpty()) return null
    if (t9(name).startsWith(digits)) return 0
    val words = plain(name).split(Regex("[^a-z0-9]+")).drop(1)
    return if (words.any { t9(it).startsWith(digits) }) 1 else null
}

// ---------- Codes ----------

private const val CODE_CHARS = "ACDEFGHJKMNPQRTUVWXY34679"

/** Random code from an alphabet without look-alikes (no 0/O, 1/I, 5/S...). */
fun randomCode(rnd: Random, length: Int = 24): String =
    (1..length).map { CODE_CHARS[rnd.nextInt(CODE_CHARS.length)] }.joinToString("")

/** 12 characters as XXXX-XXXX-XXXX. */
fun generateEmergencyCode(rnd: java.util.Random): String =
    (1..12).map { CODE_CHARS[rnd.nextInt(CODE_CHARS.length)] }.chunked(4).joinToString("-") { it.joinToString("") }

fun normalizeCode(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

fun hashEmergencyCode(code: String, salt: String): String =
    MessageDigest.getInstance("SHA-256").digest((salt + normalizeCode(code)).toByteArray())
        .joinToString("") { "%02x".format(it) }

fun Settings.checkCode(input: String): Boolean {
    val h = codeHash ?: return false
    val salt = codeSalt ?: return false
    return MessageDigest.isEqual(hashEmergencyCode(input, salt).toByteArray(), h.toByteArray())
}

// ---------- Display ----------

/** "1h 05m", "12m", "<1m". */
fun fmtDuration(ms: Long): String {
    val min = ms / MINUTE_MS
    return when {
        ms in 1 until MINUTE_MS -> "<1m"
        min >= 60 -> "%dh %02dm".format(min / 60, min % 60)
        else -> "${min}m"
    }
}
