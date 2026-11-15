package com.oheers.fish.autofish;

import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeFishingRightClickBridgeTest {
    @Test void looksUpOnlyThePlayersOwnHookWithoutScanningTheWorld() {
        Player player = mock(Player.class);
        assertNull(NativeFishingRightClickBridge.findActiveHook(player));
        FishHook hook = mock(FishHook.class);
        when(player.getFishHook()).thenReturn(hook);
        assertNull(NativeFishingRightClickBridge.findActiveHook(player));
        when(hook.isValid()).thenReturn(true);
        assertSame(hook, NativeFishingRightClickBridge.findActiveHook(player));
        verify(player, never()).getWorld();
        verify(player, never()).setRotation(anyFloat(), anyFloat());
    }
}
