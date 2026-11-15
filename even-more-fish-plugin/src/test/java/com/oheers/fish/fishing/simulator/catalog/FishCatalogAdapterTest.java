package com.oheers.fish.fishing.simulator.catalog;

import com.oheers.fish.api.fishing.CatchType;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.fishing.simulator.population.FishKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeMap;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FishCatalogAdapterTest {
    @Test
    void ordinaryUnweightedFishRemainAvailableToPreview() {
        Fish fish = fish("ordinary", 0, CatchType.BOTH);
        FishCatalogAdapter catalog = catalog(rarity("common", 4, false, fish));

        assertEquals(1, catalog.count());
        assertEquals(new FishKey("common", "ordinary"), catalog.sample(new Random(1)));
        verify(fish, never()).init();
    }

    @Test
    void invalidWeightsAndHuntOnlyOrDisabledEntriesRemainExcluded() {
        FishCatalogAdapter catalog = catalog(
                rarity("common", 4, false,
                        fish("negative", -1, CatchType.BOTH),
                        fish("nan", Double.NaN, CatchType.BOTH),
                        fish("infinite", Double.POSITIVE_INFINITY, CatchType.BOTH),
                        fish("hunt", 0, CatchType.HUNT)),
                rarity("disabled", 4, true, fish("ordinary", 0, CatchType.BOTH)),
                rarity("invalid", Double.NaN, false, fish("ordinary", 0, CatchType.BOTH)),
                rarity("zero", 0, false, fish("ordinary", 0, CatchType.BOTH)));

        assertEquals(0, catalog.count());
        assertNull(catalog.sample(new Random(1)));
    }

    @Test
    void explicitFishAndRarityWeightsStillControlSampling() {
        FishCatalogAdapter catalog = catalog(
                rarity("common", 2, false, fish("ordinary", 0, CatchType.BOTH)),
                rarity("rare", 3, false, fish("weighted", 2, CatchType.BOTH)));

        assertEquals(2, catalog.count());
        int common = 0;
        Random random = new Random(72);
        for (int i = 0; i < 10_000; i++) {
            if (catalog.sample(random).equals(new FishKey("common", "ordinary"))) common++;
        }
        assertTrue(common > 2300 && common < 2700, "Expected 2/(2+6) ordinary fish weight");
    }

    private static FishCatalogAdapter catalog(Rarity... rarities) {
        String qualifiedArtifact = System.getProperty("emf.catalog.qualifiedArtifact");
        if (qualifiedArtifact != null) {
            assertEquals(qualifiedArtifact, FishCatalogAdapter.class.getProtectionDomain().getCodeSource().getLocation().toString());
            assertEquals(qualifiedArtifact, FishManager.class.getProtectionDomain().getCodeSource().getLocation().toString());
        }
        FishManager manager = mock(FishManager.class);
        TreeMap<String, Rarity> map = new TreeMap<>();
        for (Rarity rarity : rarities) map.put(rarity.getId(), rarity);
        when(manager.getRarityMap()).thenReturn(map);
        // Only replace server catalog discovery; use the native base-weight implementation.
        try (var discovery = mockStatic(FishManager.class, CALLS_REAL_METHODS)) {
            discovery.when(FishManager::getInstance).thenReturn(manager);
            return new FishCatalogAdapter();
        }
    }

    private static Rarity rarity(String id, double weight, boolean disabled, Fish... fish) {
        Rarity rarity = mock(Rarity.class);
        when(rarity.getId()).thenReturn(id);
        when(rarity.getWeight()).thenReturn(weight);
        when(rarity.isDisabled()).thenReturn(disabled);
        when(rarity.getOriginalFishList()).thenReturn(List.of(fish));
        return rarity;
    }

    private static Fish fish(String name, double weight, CatchType catchType) {
        Fish fish = mock(Fish.class);
        when(fish.getName()).thenReturn(name);
        when(fish.getWeight()).thenReturn(weight);
        when(fish.getCatchType()).thenReturn(catchType);
        return fish;
    }
}
