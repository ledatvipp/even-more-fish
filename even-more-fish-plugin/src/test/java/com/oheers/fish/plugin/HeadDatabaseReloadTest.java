package com.oheers.fish.plugin;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.api.plugin.EMFPlugin;
import com.oheers.fish.utils.HeadDBIntegration;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.evenmorefish.fish.addons.item.HeadDatabaseItemAddon;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeadDatabaseReloadTest {
    @BeforeAll static void qualifiedOverlayClassesComeFromReviewedArtifacts() throws Exception {
        String core = System.getProperty("emf.runtime.qualifiedCore");
        if (core == null) return;
        for (Class<?> type : new Class<?>[]{EvenMoreFish.class, DependencyManager.class, HeadDBIntegration.class,
                com.oheers.fish.update.UpdateChecker.class, com.oheers.fish.api.config.ConfigBase.class}) {
            assertEquals(java.nio.file.Path.of(java.net.URI.create(core)),
                    java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
        }
        assertEquals(java.nio.file.Path.of(java.net.URI.create(System.getProperty("emf.runtime.qualifiedAddon"))),
                java.nio.file.Path.of(HeadDatabaseItemAddon.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
    }

    @Test void repeatedReloadRegistrationKeepsOneCoreHdbListener() throws Exception {
        EvenMoreFish plugin = mock(EvenMoreFish.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        DependencyManager manager = manager(plugin);
        manager.checkOptionalDependencies();
        manager.checkOptionalDependencies();
        manager.checkOptionalDependencies();
        verify(plugins).registerEvents(any(HeadDBIntegration.class), same(plugin));
    }

    @Test void addonIsReadyBeforeExactlyOneCoreOwnedReloadAndRegistrationDoesNotAccumulate() throws Exception {
        EvenMoreFish plugin = mock(EvenMoreFish.class);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("HeadDatabaseReloadTest"));
        DependencyManager manager = manager(plugin);
        when(plugin.getDependencyManager()).thenReturn(manager);
        manager.checkOptionalDependencies();
        ArgumentCaptor<HeadDBIntegration> listener = ArgumentCaptor.forClass(HeadDBIntegration.class);
        verify(plugins).registerEvents(listener.capture(), same(plugin));
        HeadDatabaseItemAddon addon = new HeadDatabaseItemAddon();
        ItemStack head = mock(ItemStack.class);
        try (MockedStatic<EvenMoreFish> core = mockStatic(EvenMoreFish.class);
             MockedStatic<EMFPlugin> api = mockStatic(EMFPlugin.class);
             MockedConstruction<HeadDatabaseAPI> heads = mockConstruction(HeadDatabaseAPI.class, (hdb, context) -> {
                 when(hdb.isHead("123")).thenReturn(true);
                 when(hdb.getItemHead("123")).thenReturn(head);
             })) {
            core.when(EvenMoreFish::getInstance).thenReturn(plugin);
            api.when(EMFPlugin::getInstance).thenReturn(plugin);
            doAnswer(call -> {
                assertSame(head, addon.getItemStack("123"));
                assertNotNull(manager.getHdbapi());
                manager.checkOptionalDependencies();
                return null;
            }).when(plugin).reload(null);
            DatabaseLoadEvent event = mock(DatabaseLoadEvent.class);
            addon.onItemsLoad(event);
            verify(plugin, never()).reload(null);
            listener.getValue().onHDBLoad(event);
            verify(plugin).reload(null);
            verify(plugins).registerEvents(any(HeadDBIntegration.class), same(plugin));
            assertEquals(2, heads.constructed().size());
        }
    }
    @Test void prioritiesGuaranteeAddonInitializationBeforeCoreReload() throws Exception {
        assertEquals(EventPriority.LOWEST, HeadDatabaseItemAddon.class.getMethod("onItemsLoad", DatabaseLoadEvent.class).getAnnotation(EventHandler.class).priority());
        assertEquals(EventPriority.MONITOR, HeadDBIntegration.class.getMethod("onHDBLoad", DatabaseLoadEvent.class).getAnnotation(EventHandler.class).priority());
    }
    private static DependencyManager manager(EvenMoreFish plugin) throws Exception {
        DependencyManager manager = new DependencyManager(plugin);
        Field enabled = DependencyManager.class.getDeclaredField("usingHeadsDB");
        enabled.setAccessible(true);
        enabled.setBoolean(manager, true);
        return manager;
    }
}
