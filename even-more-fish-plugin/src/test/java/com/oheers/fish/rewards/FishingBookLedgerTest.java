package com.oheers.fish.rewards;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FishingBookLedgerTest {
    @TempDir Path directory;
    private String url(String name) { return "jdbc:sqlite:" + directory.resolve(name); }

    @Test void receiptChoiceAndNativeBytesSurviveRestartAndCannotReroll() throws Exception {
        var owner = UUID.randomUUID();
        try (var c = DriverManager.getConnection(url("restart.db"))) {
            FishingBookLedger.initialize(c);
            var first = FishingBookLedger.admit(c, "catch:one", owner, false, "a", 2, 1000, 60000, 6);
            assertEquals("PLANNED", first.state());
        }
        try (var c = DriverManager.getConnection(url("restart.db"))) {
            var again = FishingBookLedger.admit(c, "catch:one", owner, true, "different", 1, 90000, 60000, 6);
            assertEquals("a", again.enchant()); assertEquals(2, again.level());
            FishingBookLedger.materialize(c, again, new byte[]{7, 8});
            assertArrayEquals(new byte[]{7, 8}, FishingBookLedger.materialize(c, again, new byte[]{9}).book());
            assertThrows(java.sql.SQLException.class, () -> FishingBookLedger.admit(c, "catch:one", UUID.randomUUID(), false, "a", 1, 100000, 60000, 6));
        }
    }

    @Test void missedAndLimitedDecisionsRemainFinalAcrossRetry() throws Exception {
        var owner = UUID.randomUUID();
        try (var c = DriverManager.getConnection(url("miss.db"))) {
            FishingBookLedger.initialize(c);
            FishingBookLedger.admit(c, "catch:miss", owner, false, "", 0, 1000, 60000, 6);
            assertEquals("MISS", FishingBookLedger.admit(c, "catch:miss", owner, false, "a", 1, 2000, 60000, 6).state());
            FishingBookLedger.admit(c, "catch:win", owner, false, "a", 1, 3000, 60000, 6);
            assertEquals("LIMITED", FishingBookLedger.admit(c, "catch:limited", owner, true, "a", 1, 4000, 60000, 6).state());
            assertEquals("LIMITED", FishingBookLedger.admit(c, "catch:limited", owner, false, "a", 1, 90000, 60000, 6).state());
        }
    }

    @Test void manualAndAutoSharePersistentCooldownAndHourlyQuota() throws Exception {
        var owner = UUID.randomUUID();
        try (var c = DriverManager.getConnection(url("quota.db"))) {
            FishingBookLedger.initialize(c);
            for (int i = 0; i < 6; i++) assertEquals("PLANNED", FishingBookLedger.admit(c, "catch:" + i, owner, i % 2 == 0, "a", 1, i * 60000L, 60000, 6).state());
        }
        try (var c = DriverManager.getConnection(url("quota.db"))) {
            assertEquals("LIMITED", FishingBookLedger.admit(c, "catch:seventh", owner, true, "a", 1, 360000, 60000, 6).state());
            assertEquals("PLANNED", FishingBookLedger.admit(c, "catch:expired", owner, false, "a", 1, 3600001, 60000, 6).state());
        }
    }

    @Test void onlyOneClaimWinsAndUncertainClaimNeverAutomaticallyRepays() throws Exception {
        var owner = UUID.randomUUID();
        try (var c = DriverManager.getConnection(url("claim.db"))) {
            FishingBookLedger.initialize(c);
            var planned = FishingBookLedger.admit(c, "catch:claim", owner, false, "a", 1, 1000, 60000, 6);
            var ready = FishingBookLedger.materialize(c, planned, new byte[]{1});
            // Full inventories do not enter the CAS: native frozen bytes remain READY.
            assertEquals("READY", FishingBookLedger.find(c, ready.receipt(), owner).state());
            assertTrue(FishingBookLedger.transition(c, ready, "READY", "CLAIMING"));
            assertFalse(FishingBookLedger.transition(c, ready, "READY", "CLAIMING"));
            assertEquals("CLAIMING", FishingBookLedger.pending(c, owner).getFirst().state());
            assertThrows(IllegalArgumentException.class, () -> FishingBookLedger.transition(c, ready, "CLAIMED", "READY"));
            FishingBookLedger.transition(c, ready, "CLAIMING", "CLAIMED");
            assertTrue(FishingBookLedger.pending(c, owner).isEmpty());
        }
    }

    @Test void uncertainBacklogDoesNotStarveReadyBooks() throws Exception {
        var owner = UUID.randomUUID();
        try (var c = DriverManager.getConnection(url("backlog.db"))) {
            FishingBookLedger.initialize(c);
            for (int i = 0; i < 64; i++) {
                var entry = FishingBookLedger.admit(c, "catch:" + i, owner, false, "a", 1, i * 60000L, 0, 1000);
                entry = FishingBookLedger.materialize(c, entry, new byte[]{1});
                FishingBookLedger.transition(c, entry, "READY", "CLAIMING");
            }
            var ready = FishingBookLedger.admit(c, "catch:ready", owner, true, "a", 1, 3900000, 0, 1000);
            FishingBookLedger.materialize(c, ready, new byte[]{2});
            assertEquals("catch:ready", FishingBookLedger.pending(c, owner).getFirst().receipt());
        }
    }
}
