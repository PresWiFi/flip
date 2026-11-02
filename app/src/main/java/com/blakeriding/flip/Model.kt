package com.blakeriding.flip

import kotlinx.serialization.Serializable

/** ISO day numbers: 1 = Monday ... 7 = Sunday. */
val ALL_DAYS: Set<Int> = (1..7).toSet()

@Serializable
enum class TagKind { NFC, BARCODE }

/** Something physical to scan to unlock: an NFC card/sticker (value = UID hex) or a barcode/QR (value = raw text). */
@Serializable
data class Tag(val id: String, val name: String, val kind: TagKind, val value: String)

/**
 * Blocked hours. Minutes after midnight. If [endMin] <= [startMin] it runs past midnight
 * (e.g. 22:00-07:00), and [days] are the days it starts on.
 */
@Serializable
data class Window(val startMin: Int, val endMin: Int, val days: Set<Int> = ALL_DAYS)

/** The rules for one app. */
@Serializable
data class AppRule(
    val pkg: String,
    /** Locked every time you open it. */
    val always: Boolean = false,
    /** Minutes per day, or null for no limit. */
    val dailyLimitMin: Int? = null,
    val schedules: List<Window> = emptyList(),
    /** Clear this app's notifications while it's blocked. */
    val hideNotifs: Boolean = false,
)

/** Several apps sharing one daily limit (e.g. all social apps 45 min together). */
@Serializable
data class Group(val id: String, val name: String, val pkgs: Set<String>, val limitMin: Int)

@Serializable
enum class UnlockMethod { QUICK, TAG, CODE, EMERGENCY }

/** An app unlocked until [until] (epoch ms). */
@Serializable
data class Grant(val pkg: String, val until: Long, val method: UnlockMethod)

@Serializable
enum class LogKind {
    UNLOCK_QUICK, UNLOCK_TAG, UNLOCK_CODE, UNLOCK_EMERGENCY,
    BLOCKED_SCREEN, WRONG_CODE, RULES_LOOSENED, PROTECTION_OFF,
    ADMIN_OFF_ATTEMPT, ADMIN_OFF, GUARD_OFF, FORCE_STOPPED,
}

@Serializable
data class LogEvent(val at: Long, val kind: LogKind, val detail: String = "")

@Serializable
enum class Look { V3, MODERN, V3_OUTER }

/** The V3's finishes. */
@Serializable
enum class V3Color { SILVER, BLACK, PINK }

@Serializable
enum class KeypadMode {
    /** Digits dial, and also search apps and contacts by T9. */
    T9,
    /** Digits only dial. */
    DIAL,
    /** No keypad on the idle screen. */
    NONE,
}

@Serializable
data class Settings(
    val look: Look = Look.V3,
    val v3Color: V3Color = V3Color.SILVER,
    val keypad: KeypadMode = KeypadMode.T9,
    /** The 9 main-menu slots: a package name, or null for an empty slot. */
    val favorites: List<String?> = List(9) { null },
    val protectionOn: Boolean = false,
    val codeHash: String? = null,
    val codeSalt: String? = null,
    val unlockMin: Int = 15,
    val quickMin: Int = 5,
    val quickPerDay: Int = 3,
    val clock24h: Boolean = false,
    /** Show app icons as chunky pixels in the V3 looks. */
    val pixelIcons: Boolean = true,
)

@Serializable
data class AppState(
    val rules: List<AppRule> = emptyList(),
    val groups: List<Group> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val grants: List<Grant> = emptyList(),
    val log: List<LogEvent> = emptyList(),
    val settings: Settings = Settings(),
    /** Last process exit already checked for force-stops (ApplicationExitInfo timestamp). */
    val exitsCheckedAt: Long = 0,
)
