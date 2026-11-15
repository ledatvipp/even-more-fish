package com.oheers.fish.autofish;

import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Calendar weeks share the same reset even after a restart or permission change. */
public final class AutoFishWeeklyQuota {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int[] TIER_MINUTES = {840, 420, 180};

    private AutoFishWeeklyQuota() { }

    public static long weekStart(Instant now) {
        return now.atZone(ZONE).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
    }

    public static long limitSeconds(Player player) {
        for (int minutes : TIER_MINUTES) {
            if (player.hasPermission("emf.autofish.weekly." + minutes)) return minutes * 60L;
        }
        return 3600L;
    }

    public static int usedPercent(long used, long limit) {
        return limit <= 0L ? 100 : (int) Math.min(100D, Math.max(0D, used * 100D / limit));
    }

    public static String progressBar(int percent) {
        int filled = Math.max(0, Math.min(20, percent * 20 / 100));
        return "<aqua>" + "▰".repeat(filled) + "</aqua><dark_gray>" + "▱".repeat(20 - filled) + "</dark_gray>";
    }
}
