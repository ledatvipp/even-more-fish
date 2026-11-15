package com.oheers.fish.effects;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Rarity;
import org.junit.jupiter.api.Test;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import com.oheers.fish.selling.WorthNBT;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class VanillaCatchCinematicServiceTest {
    @Test void twoTickFramesPreserveEveryTiersHeartbeatCount() {
        for (var tier : CatchRollTimeline.TierStyle.values()) {
            var durations = CatchRollTimeline.Durations.forTier(tier);
            int beats = 0;
            for (int elapsed = 0; elapsed < durations.totalTicks(); elapsed += 2) {
                if (CatchRollTimeline.sample(durations, elapsed).phase() == CatchRollTimeline.Phase.ROLLING
                        && VanillaCatchCinematicService.heartbeatInFrame(durations, tier, elapsed)) beats++;
            }
            assertEquals(tier.heartbeatCount(), beats, tier.name());
        }
    }

    @Test void celebrationStaysLocalRespectsVisibilityAndCapsViewersAndRepeatedCatches() {
        var service = new VanillaCatchCinematicService(mock(EvenMoreFish.class));
        World world = mock(World.class);
        Location water = new Location(world, 0, 64, 0);
        Player catcher = mock(Player.class);
        when(catcher.getWorld()).thenReturn(world);
        when(catcher.getUniqueId()).thenReturn(UUID.randomUUID());
        var viewers = new ArrayList<Player>();
        Player hidden = mock(Player.class);
        viewers.add(hidden);
        Player distant = mock(Player.class);
        when(distant.canSee(catcher)).thenReturn(true);
        when(distant.getLocation()).thenReturn(new Location(world, 33, 64, 0));
        viewers.add(distant);
        for (int i = 0; i < 34; i++) {
            Player viewer = mock(Player.class);
            when(viewer.canSee(catcher)).thenReturn(true);
            when(viewer.getLocation()).thenReturn(water);
            viewers.add(viewer);
        }
        when(world.getPlayers()).thenReturn(viewers);
        Fish fish = mock(Fish.class);
        Rarity rarity = mock(Rarity.class);
        when(fish.getRarity()).thenReturn(rarity);
        when(rarity.getId()).thenReturn("Legendary");
        try (var main = mockStatic(MainConfig.class); var worth = mockStatic(WorthNBT.class)) {
            MainConfig config = mock(MainConfig.class, RETURNS_DEEP_STUBS);
            main.when(MainConfig::getInstance).thenReturn(config);
            when(config.getConfig().getBoolean("animations.rare-catch.enabled", true)).thenReturn(true);
            worth.when(() -> WorthNBT.getValue(fish)).thenReturn(Optional.of(1D));
            service.celebrateCatch(catcher, water, fish);
            service.celebrateCatch(catcher, water, fish);
        }
        verify(viewers.get(2), times(34)).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        for (Player excluded : new Player[]{hidden, distant, viewers.get(34), viewers.get(35)}) {
            verify(excluded, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        }
    }

    @Test void celebratesRareLargeOrValuableCatchesWithoutTreatingInvalidMetricsAsExceptional() {
        assertTrue(VanillaCatchCinematicService.isExceptionalCatch(CatchRollTimeline.TierStyle.LEGENDARY, 1, 0));
        assertTrue(VanillaCatchCinematicService.isExceptionalCatch(CatchRollTimeline.TierStyle.MYTHICAL, 1, 0));
        assertTrue(VanillaCatchCinematicService.isExceptionalCatch(CatchRollTimeline.TierStyle.STANDARD, 200, 0));
        assertTrue(VanillaCatchCinematicService.isExceptionalCatch(CatchRollTimeline.TierStyle.STANDARD, 1, 50_000));
        assertFalse(VanillaCatchCinematicService.isExceptionalCatch(CatchRollTimeline.TierStyle.EPIC, 199, 49_999));
        assertFalse(VanillaCatchCinematicService.isExceptionalCatch(CatchRollTimeline.TierStyle.STANDARD, Float.NaN, Double.POSITIVE_INFINITY));
    }
    @Test void commonNeverOpensACinematicEvenWithBroadRaritySettings() {
        var service = new VanillaCatchCinematicService(mock(EvenMoreFish.class));
        Fish fish = mock(Fish.class);
        Rarity rarity = mock(Rarity.class);
        when(fish.getRarity()).thenReturn(rarity);
        when(rarity.getId()).thenReturn("cOmMoN");
        try (var main = mockStatic(MainConfig.class)) {
            MainConfig config = mock(MainConfig.class, RETURNS_DEEP_STUBS);
            main.when(MainConfig::getInstance).thenReturn(config);
            when(config.getConfig().getBoolean("animations.rare-catch.cinematic.all-rarities", true)).thenReturn(true);
            assertTrue(service.eligible(fish));
        }
    }
}
