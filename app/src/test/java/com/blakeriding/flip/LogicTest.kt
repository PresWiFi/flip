package com.blakeriding.flip

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class LogicTest {
    private val zone = ZoneId.of("America/New_York")

    // Wednesday 2026-10-28
    private fun at(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone)
    private fun ms(day: Int, h: Int, m: Int = 0) = at(day, h, m).ms()
    private fun min(n: Int) = n * MINUTE_MS

    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"

    // ---------- Schedules ----------

    @Test fun windowActiveInside() {
        val w = Window(9 * 60, 17 * 60)
        assertEquals(at(28, 17), w.activeUntil(at(28, 12)))
        assertNull(w.activeUntil(at(28, 8, 59)))
        assertNull(w.activeUntil(at(28, 17)))
    }

    @Test fun overnightWindowCarriesIntoNextDay() {
        val w = Window(22 * 60, 7 * 60, days = setOf(3)) // starts Wednesdays
        assertEquals(at(29, 7), w.activeUntil(at(28, 23)))
        assertEquals(at(29, 7), w.activeUntil(at(29, 6)))
        assertNull(w.activeUntil(at(29, 7)))
        assertNull(w.activeUntil(at(29, 23))) // Thursday isn't a start day
    }

    @Test fun windowRespectsDays() {
        val w = Window(9 * 60, 17 * 60, days = setOf(1, 2, 3, 4, 5))
        assertNotNull(w.activeUntil(at(28, 10)))
        assertNull(w.activeUntil(at(31, 10))) // Saturday
    }

    @Test fun startEqualsEndIsAllDay() {
        val w = Window(0, 0, days = setOf(3))
        assertEquals(at(29, 0), w.activeUntil(at(28, 15)))
    }

    @Test fun coversLongerWindow() {
        assertTrue(Window(21 * 60, 8 * 60).covers(Window(22 * 60, 7 * 60)))
        assertFalse(Window(23 * 60, 7 * 60).covers(Window(22 * 60, 7 * 60)))
        assertTrue(Window(22 * 60, 7 * 60).covers(Window(60, 5 * 60, days = setOf(4)))) // 1-5am inside the night
        assertFalse(Window(9 * 60, 17 * 60, days = setOf(1)).covers(Window(9 * 60, 17 * 60, days = setOf(1, 2))))
    }

    // ---------- Usage ----------

    @Test fun foregroundTimeAddsSessions() {
        val ev = listOf(
            UsageEv(ig, ms(28, 9), true), UsageEv(ig, ms(28, 9, 10), false),
            UsageEv(yt, ms(28, 10), true), UsageEv(yt, ms(28, 10, 30), false),
            UsageEv(ig, ms(28, 11), true), UsageEv(ig, ms(28, 11, 5), false),
        )
        val t = foregroundTime(ev, ms(28, 0), ms(28, 12))
        assertEquals(min(15), t[ig])
        assertEquals(min(30), t[yt])
    }

    @Test fun openAppCountsUntilNow() {
        val t = foregroundTime(listOf(UsageEv(ig, ms(28, 9), true)), ms(28, 0), ms(28, 9, 20))
        assertEquals(min(20), t[ig])
    }

    @Test fun appOpenAtMidnightCountsFromMidnight() {
        val t = foregroundTime(listOf(UsageEv(ig, ms(28, 0, 10), false)), ms(28, 0), ms(28, 9))
        assertEquals(min(10), t[ig])
    }

    @Test fun switchingWithoutPauseClosesPreviousApp() {
        val ev = listOf(UsageEv(ig, ms(28, 9), true), UsageEv(yt, ms(28, 9, 5), true), UsageEv(yt, ms(28, 9, 6), false))
        val t = foregroundTime(ev, ms(28, 0), ms(28, 10))
        assertEquals(min(5), t[ig])
        assertEquals(min(1), t[yt])
    }

    @Test fun activityChangeInsideAnAppIsContinuous() {
        val ev = listOf(
            UsageEv(ig, ms(28, 9), true), UsageEv(ig, ms(28, 9, 5), false),
            UsageEv(ig, ms(28, 9, 5), true), UsageEv(ig, ms(28, 9, 8), false),
        )
        assertEquals(min(8), foregroundTime(ev, ms(28, 0), ms(28, 10))[ig])
    }

    // ---------- Verdicts ----------

    @Test fun noRulesIsFree() {
        assertEquals(Verdict.FREE, AppState().verdict(ig, emptyMap(), at(28, 12)))
    }

    @Test fun alwaysBlocked() {
        val s = AppState(rules = listOf(AppRule(ig, always = true)))
        val v = s.verdict(ig, emptyMap(), at(28, 12))
        assertTrue(v.blocked)
        assertEquals(Reason.ALWAYS, v.reason)
    }

    @Test fun limitBlocksWhenUsedUpUntilMidnight() {
        val s = AppState(rules = listOf(AppRule(ig, dailyLimitMin = 30)))
        val ok = s.verdict(ig, mapOf(ig to min(20)), at(28, 12))
        assertFalse(ok.blocked)
        assertEquals(min(10), ok.leftMs)
        val v = s.verdict(ig, mapOf(ig to min(30)), at(28, 12))
        assertTrue(v.blocked)
        assertEquals(Reason.LIMIT, v.reason)
        assertEquals(ms(29, 0), v.blockedUntil)
    }

    @Test fun scheduleBlocks() {
        val s = AppState(rules = listOf(AppRule(ig, schedules = listOf(Window(22 * 60, 7 * 60)))))
        assertTrue(s.verdict(ig, emptyMap(), at(28, 23)).blocked)
        assertEquals(ms(29, 7), s.verdict(ig, emptyMap(), at(28, 23)).blockedUntil)
        assertFalse(s.verdict(ig, emptyMap(), at(28, 12)).blocked)
    }

    @Test fun groupLimitIsShared() {
        val s = AppState(groups = listOf(Group("g", "Social", setOf(ig, yt), 45)))
        val usage = mapOf(ig to min(30), yt to min(15))
        val v = s.verdict(ig, usage, at(28, 12))
        assertTrue(v.blocked)
        assertEquals(Reason.GROUP, v.reason)
        assertEquals("Social", v.groupName)
        assertEquals(min(5), s.verdict(yt, mapOf(ig to min(30), yt to min(10)), at(28, 12)).leftMs)
    }

    @Test fun grantOverridesEveryBlock() {
        val s = AppState(rules = listOf(AppRule(ig, always = true, schedules = listOf(Window(0, 0)))))
            .unlock(ig, UnlockMethod.TAG, at(28, 12))!!
        val v = s.verdict(ig, emptyMap(), at(28, 12, 10))
        assertFalse(v.blocked)
        assertEquals(ms(28, 12, 15), v.grantUntil)
        assertTrue(s.verdict(ig, emptyMap(), at(28, 12, 15)).blocked)
    }

    @Test fun grantIsPerApp() {
        val s = AppState(rules = listOf(AppRule(ig, always = true), AppRule(yt, always = true)))
            .unlock(ig, UnlockMethod.CODE, at(28, 12))!!
        assertTrue(s.verdict(yt, emptyMap(), at(28, 12, 1)).blocked)
    }

    // ---------- Quick unlocks ----------

    @Test fun quickUnlocksRunOutAndResetAtMidnight() {
        var s = AppState(rules = listOf(AppRule(ig, always = true)))
        repeat(3) { i -> s = s.unlock(ig, UnlockMethod.QUICK, at(28, 9 + i))!! }
        assertEquals(0, s.quickLeft(at(28, 15)))
        assertNull(s.unlock(ig, UnlockMethod.QUICK, at(28, 15)))
        assertEquals(3, s.quickLeft(at(29, 0, 1)))
    }

    @Test fun quickUnlockIsFiveMinutes() {
        val s = AppState().unlock(ig, UnlockMethod.QUICK, at(28, 12))!!
        assertEquals(ms(28, 12, 5), s.grants.single().until)
        assertEquals(LogKind.UNLOCK_QUICK, s.log.single().kind)
    }

    @Test fun unlockDropsExpiredGrants() {
        val s = AppState().unlock(ig, UnlockMethod.TAG, at(28, 9))!!.unlock(yt, UnlockMethod.TAG, at(28, 12))!!
        assertEquals(listOf(yt), s.grants.map { it.pkg })
    }

    // ---------- Loosening ----------

    private val base = AppState(
        rules = listOf(AppRule(ig, always = true, dailyLimitMin = 30, schedules = listOf(Window(22 * 60, 7 * 60)), hideNotifs = true)),
        groups = listOf(Group("g", "Social", setOf(ig, yt), 45)),
    )

    @Test fun tighteningIsFree() {
        val tighter = base.copy(
            rules = listOf(base.rules[0].copy(dailyLimitMin = 20, schedules = listOf(Window(21 * 60, 8 * 60))), AppRule(yt, always = true)),
            groups = listOf(base.groups[0].copy(limitMin = 30, pkgs = setOf(ig, yt, "x"))),
            settings = base.settings.copy(quickPerDay = 1),
        )
        assertEquals(emptyList<String>(), loosenings(base, tighter))
    }

    @Test fun looseningIsCaught() {
        fun rule(f: (AppRule) -> AppRule) = base.copy(rules = listOf(f(base.rules[0])))
        assertEquals(1, loosenings(base, base.copy(rules = emptyList())).size)
        assertEquals(1, loosenings(base, rule { it.copy(always = false) }).size)
        assertEquals(1, loosenings(base, rule { it.copy(dailyLimitMin = 45) }).size)
        assertEquals(1, loosenings(base, rule { it.copy(dailyLimitMin = null) }).size)
        assertEquals(1, loosenings(base, rule { it.copy(schedules = listOf(Window(23 * 60, 7 * 60))) }).size)
        assertEquals(1, loosenings(base, rule { it.copy(hideNotifs = false) }).size)
        assertEquals(1, loosenings(base, base.copy(groups = emptyList())).size)
        assertEquals(1, loosenings(base, base.copy(groups = listOf(base.groups[0].copy(limitMin = 60)))).size)
        assertEquals(1, loosenings(base, base.copy(groups = listOf(base.groups[0].copy(pkgs = setOf(ig))))).size)
        assertEquals(1, loosenings(base, base.copy(tags = listOf(Tag("t", "Desk", TagKind.NFC, "AB")))).size)
        assertEquals(1, loosenings(base, base.copy(settings = base.settings.copy(quickPerDay = 4))).size)
        assertEquals(1, loosenings(base, base.copy(settings = base.settings.copy(unlockMin = 30))).size)
        val on = base.copy(settings = base.settings.copy(protectionOn = true))
        assertEquals(1, loosenings(on, base).size)
    }

    @Test fun lookChangesAreFree() {
        assertTrue(loosenings(base, base.copy(settings = base.settings.copy(look = Look.MODERN, keypad = KeypadMode.NONE))).isEmpty())
    }

    // ---------- T9 ----------

    @Test fun t9Digits() {
        assertEquals("467824726", t9("Instagram"))
        assertEquals("77689", t9("Spöt!y"))
    }

    @Test fun t9MatchesStartOfNameFirst() {
        assertEquals(0, t9Rank("467", "Instagram"))
        assertEquals(1, t9Rank("3", "Google Drive"))
        assertNull(t9Rank("9", "Instagram"))
        assertNull(t9Rank("", "Instagram"))
    }

    // ---------- Codes ----------

    @Test fun emergencyCodeChecks() {
        val code = generateEmergencyCode(java.util.Random(1))
        val s = Settings(codeHash = hashEmergencyCode(code, "salt"), codeSalt = "salt")
        assertTrue(s.checkCode(code.lowercase().replace("-", " ")))
        assertFalse(s.checkCode("AAAA-AAAA-AAAA"))
        assertFalse(Settings().checkCode(code))
    }

    @Test fun typedCodeIs24Chars() {
        val c = randomCode(Random(1))
        assertEquals(24, c.length)
        assertEquals(c, normalizeCode(c.chunked(4).joinToString(" ")))
    }

    @Test fun durations() {
        assertEquals("<1m", fmtDuration(5_000))
        assertEquals("12m", fmtDuration(min(12)))
        assertEquals("1h 05m", fmtDuration(min(65)))
    }

    // ---------- Storage ----------

    @Test fun stateRoundTrips() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = base.unlock(ig, UnlockMethod.QUICK, at(28, 12))!!.copy(tags = listOf(Tag("t", "Desk", TagKind.BARCODE, "123")))
        assertEquals(s, json.decodeFromString(AppState.serializer(), json.encodeToString(AppState.serializer(), s)))
    }
}
