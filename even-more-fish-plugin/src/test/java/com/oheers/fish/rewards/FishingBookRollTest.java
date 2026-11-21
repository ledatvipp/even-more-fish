package com.oheers.fish.rewards;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FishingBookRollTest {
    @Test void explicitPrivateDrawIsStableRegardlessOfPoolOrder() {
        var pool = List.of(new FishingBookRoll.Enchant("a", "SIMPLE", 2), new FishingBookRoll.Enchant("b", "ELITE", 3));
        var weights = Map.of("SIMPLE", 50.0, "ELITE", 15.0);
        var a = new java.util.Random(42); var b = new java.util.Random(42);
        assertEquals(FishingBookRoll.wins(.005, a), FishingBookRoll.wins(.005, b));
        assertEquals(FishingBookRoll.choose(pool, weights, a), FishingBookRoll.choose(pool.reversed(), weights, b));
    }
    @Test void chanceBoundsAndExcludedRaritiesAreReal() {
        assertInstanceOf(java.security.SecureRandom.class, FishingBookRoll.random());
        assertFalse(FishingBookRoll.wins(0, new java.util.Random(0)));
        assertTrue(FishingBookRoll.wins(1, new java.util.Random(0)));
        assertThrows(IllegalArgumentException.class, () -> FishingBookRoll.wins(Double.NaN, new java.util.Random(0)));
        var pool = List.of(new FishingBookRoll.Enchant("ordinary", "SIMPLE", 2), new FishingBookRoll.Enchant("excluded", "SOUL", 5));
        assertEquals("ordinary", FishingBookRoll.choose(pool, Map.of("SIMPLE", 1.0), new java.util.Random(0)).id());
    }
}
