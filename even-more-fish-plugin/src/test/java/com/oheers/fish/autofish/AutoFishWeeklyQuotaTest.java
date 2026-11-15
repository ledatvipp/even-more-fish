package com.oheers.fish.autofish;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutoFishWeeklyQuotaTest {
    @Test void resetsAtMondayMidnightVietnamTime() {
        long monday = LocalDate.of(2026, 11, 9).toEpochDay();
        assertEquals(monday, AutoFishWeeklyQuota.weekStart(Instant.parse("2026-11-15T16:59:59Z")));
        assertEquals(monday + 7, AutoFishWeeklyQuota.weekStart(Instant.parse("2026-11-15T17:00:00Z")));
        assertEquals(monday + 7, AutoFishWeeklyQuota.weekStart(Instant.parse("2026-11-16T12:00:00Z")));
    }

    @Test void highestGrantedPermissionWins() {
        Player player = mock(Player.class);
        assertEquals(3600L, AutoFishWeeklyQuota.limitSeconds(player));
        when(player.hasPermission("emf.autofish.weekly.180")).thenReturn(true);
        assertEquals(10800L, AutoFishWeeklyQuota.limitSeconds(player));
        when(player.hasPermission("emf.autofish.weekly.420")).thenReturn(true);
        assertEquals(25200L, AutoFishWeeklyQuota.limitSeconds(player));
        when(player.hasPermission("emf.autofish.weekly.840")).thenReturn(true);
        assertEquals(50400L, AutoFishWeeklyQuota.limitSeconds(player));
    }

    @Test void consumesWeeklyAllowanceBeforePurchasedTimeAndPreservesUsageAcrossTierChanges() {
        AutoFishProfile profile = AutoFishProfile.createDefault(UUID.randomUUID(), 2);
        assertTrue(profile.consumeAutoSecond(10, 2));
        assertTrue(profile.consumeAutoSecond(10, 2));
        assertEquals(2, profile.getRemainingSeconds());
        assertEquals(0, profile.getWeeklyRemainingSeconds(10, 2));
        assertEquals(2, profile.getWeeklyRemainingSeconds(10, 4));
        assertEquals(0, profile.getWeeklyRemainingSeconds(10, 1));
        assertTrue(profile.consumeAutoSecond(10, 2));
        assertTrue(profile.consumeAutoSecond(10, 2));
        assertFalse(profile.consumeAutoSecond(10, 2));
        assertEquals(2, profile.getWeeklyUsedSeconds());
    }

    @Test void resetDoesNotAccumulateOrRemovePurchasedTimeAndSurvivesPersistence() {
        AutoFishProfile profile = AutoFishProfile.createDefault(UUID.randomUUID(), 300);
        profile.restoreWeeklyUsage(10, 3600);
        assertEquals(0, profile.getWeeklyRemainingSeconds(10, 3600));
        assertEquals(0, profile.getWeeklyRemainingSeconds(3, 3600));
        assertEquals(3600, profile.getWeeklyRemainingSeconds(31, 3600));
        assertEquals(300, profile.getRemainingSeconds());
        assertEquals(0, profile.getWeeklyUsedSeconds());
        assertEquals(31, profile.getWeeklyStart());
        assertTrue(profile.isDirty());
    }

    @Test void progressIsUsedQuotaAndStaysWithinBounds() {
        assertEquals(0, AutoFishWeeklyQuota.usedPercent(0, 3600));
        assertEquals(50, AutoFishWeeklyQuota.usedPercent(1800, 3600));
        assertEquals(100, AutoFishWeeklyQuota.usedPercent(7200, 3600));
        assertEquals(0, AutoFishWeeklyQuota.usedPercent(-1, 3600));
        assertEquals(100, AutoFishWeeklyQuota.usedPercent(1, 0));
        assertEquals("<aqua>▰▰▰▰▰▰▰▰▰▰</aqua><dark_gray>▱▱▱▱▱▱▱▱▱▱</dark_gray>", AutoFishWeeklyQuota.progressBar(50));
    }
}
