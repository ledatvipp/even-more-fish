package com.oheers.fish.rewards;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FishingBookRollTest {
    @Test void nativeCatchReceiptHasStableDecisionAndSelectedId() {
        var pool = List.of(new FishingBookRoll.Enchant("a", "SIMPLE", 2), new FishingBookRoll.Enchant("b", "ELITE", 3));
        var weights = Map.of("SIMPLE", 50.0, "ELITE", 15.0);
        var a = FishingBookRoll.random("catch:one"); var b = FishingBookRoll.random("catch:one");
        assertEquals(FishingBookRoll.wins(.005, a), FishingBookRoll.wins(.005, b));
        assertEquals(FishingBookRoll.choose(pool, weights, a), FishingBookRoll.choose(pool.reversed(), weights, b));
    }
    @Test void chanceBoundsAndExcludedRaritiesAreReal() {
        assertFalse(FishingBookRoll.wins(0, FishingBookRoll.random("zero")));
        assertTrue(FishingBookRoll.wins(1, FishingBookRoll.random("one")));
        assertThrows(IllegalArgumentException.class, () -> FishingBookRoll.wins(Double.NaN, FishingBookRoll.random("invalid")));
        var pool = List.of(new FishingBookRoll.Enchant("ordinary", "SIMPLE", 2), new FishingBookRoll.Enchant("excluded", "SOUL", 5));
        assertEquals("ordinary", FishingBookRoll.choose(pool, Map.of("SIMPLE", 1.0), FishingBookRoll.random("group")).id());
    }
}
