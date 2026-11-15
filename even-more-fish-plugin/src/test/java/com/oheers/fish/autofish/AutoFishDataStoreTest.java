package com.oheers.fish.autofish;

import com.oheers.fish.EvenMoreFish;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutoFishDataStoreTest {
    @TempDir Path directory;

    @Test void restartRetainsWeeklyUsageAndExistingPurchasedBalance() {
        EvenMoreFish plugin = mock(EvenMoreFish.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        AutoFishDataStore store = new AutoFishDataStore(plugin);
        UUID id = UUID.randomUUID();
        AutoFishProfile profile = store.getOrCreate(id);
        profile.addSeconds(700);
        profile.restoreWeeklyUsage(20766, 1800);
        store.saveAllSync();
        AutoFishProfile restored = new AutoFishDataStore(plugin).getOrCreate(id);
        assertEquals(1000, restored.getRemainingSeconds());
        assertEquals(20766, restored.getWeeklyStart());
        assertEquals(1800, restored.getWeeklyUsedSeconds());
        assertEquals(1800, restored.getWeeklyRemainingSeconds(20766, 3600));
    }
}
