package com.oheers.fish.api.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigUpdateDiagnosticsTest {
    @Test void updaterWarningContainsBoundedTypesWithoutSensitiveMessages() throws Exception {
        ConfigBase config = mock(ConfigBase.class, CALLS_REAL_METHODS);
        Plugin plugin = mock(Plugin.class);
        Logger logger = mock(Logger.class);
        YamlDocument document = mock(YamlDocument.class);
        when(plugin.getLogger()).thenReturn(logger);
        set(config, "plugin", plugin);
        set(config, "configUpdater", true);
        set(config, "fileName", "messages.yml");
        set(config, "config", document);
        IOException failure = new IOException("private-location-and-credential", new UnsupportedOperationException("private-content"));
        doThrow(failure).when(document).update();

        config.update();

        verify(logger).warning("Failed to update messages.yml (IOException <- UnsupportedOperationException)");
        verify(document).update();
        verify(document, never()).save();
    }
    @Test void causeChainIsBoundedEvenForCycles() {
        Throwable failure = mock(Throwable.class);
        when(failure.getCause()).thenReturn(failure);
        String summary = ConfigBase.updateFailureTypes(failure);
        assertEquals(4, summary.split(" <- ").length);
        assertTrue(summary.length() < 200);
        verify(failure, never()).getMessage();
    }
    @Test void newerFilePreservesRawBytesHeaderAndCustomLore(@TempDir Path folder) throws Exception {
        Fixture fixture = fixture(folder, "version: 15\nfish-lore:\n  - '<aqua>Custom lore preserved</aqua>'\n", "version: 13\nfish-lore: [default]\n", false, true);
        byte[] before = Files.readAllBytes(fixture.file());
        String documentBefore = fixture.document().dump();
        fixture.config().update();
        assertArrayEquals(before, Files.readAllBytes(fixture.file()));
        assertEquals(documentBefore, fixture.document().dump());
        assertEquals(15, fixture.document().getInt("version"));
        verify(fixture.document(), never()).update();
        verify(fixture.logger()).info("Preserving messages.yml: file version 15 is newer than bundled defaults 13; automatic downgrade disabled.");
        verify(fixture.logger(), never()).warning(anyString());
    }
    @Test void equalAndOlderVersionsUseRealUpdater(@TempDir Path folder) throws Exception {
        for (int version : new int[]{12, 13}) {
            Fixture fixture = fixture(folder, "version: " + version + "\ncustom: preserved\n", "version: 13\ncustom: default\n", false, true);
            fixture.config().update();
            verify(fixture.document()).update();
            assertEquals("13", fixture.document().getString("version"));
            assertEquals("preserved", fixture.document().getString("custom"));
            verify(fixture.logger(), never()).info(anyString());
            verify(fixture.logger(), never()).warning(anyString());
        }
    }
    @Test void explicitlyAllowedDowngradeStillUsesUpdater(@TempDir Path folder) throws Exception {
        Fixture fixture = fixture(folder, "version: 15\ncustom: preserved\n", "version: 13\ncustom: default\n", true, true);
        fixture.config().update();
        verify(fixture.document()).update();
        assertEquals("13", fixture.document().getString("version"));
        verify(fixture.logger(), never()).info(anyString());
    }
    @Test void missingDefaultsOrVersioningKeepsExistingUpdaterPath(@TempDir Path folder) throws Exception {
        Fixture withoutDefaults = fixture(folder, "version: 15\n", null, false, true);
        withoutDefaults.config().update();
        verify(withoutDefaults.document()).update();
        verify(withoutDefaults.logger(), never()).info(anyString());
        Fixture withoutVersioning = fixture(folder, "version: 15\n", "version: 13\n", false, false);
        withoutVersioning.config().update();
        verify(withoutVersioning.document()).update();
        verify(withoutVersioning.logger(), never()).info(anyString());
    }
    @Test void missingCurrentVersionKeepsRealUpgradePath(@TempDir Path folder) throws Exception {
        Fixture fixture = fixture(folder, "custom: preserved\n", "version: 13\ncustom: default\n", false, true);
        fixture.config().update();
        verify(fixture.document()).update();
        assertEquals("13", fixture.document().getString("version"));
        verify(fixture.logger(), never()).info(anyString());
    }
    @Test void disabledUpdaterDoesNotReadMetadataOrWrite(@TempDir Path folder) throws Exception {
        Fixture fixture = fixture(folder, "version: 15\n", "version: 13\n", false, true);
        set(fixture.config(), "configUpdater", false);
        fixture.config().update();
        verifyNoInteractions(fixture.document());
        verifyNoInteractions(fixture.logger());
    }
    @Test void unsupportedFailureOutsideNewerSchemaGuardStillWarns() throws Exception {
        ConfigBase config = mock(ConfigBase.class, CALLS_REAL_METHODS);
        Plugin plugin = mock(Plugin.class);
        Logger logger = mock(Logger.class);
        YamlDocument document = mock(YamlDocument.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(document.getUpdaterSettings()).thenReturn(UpdaterSettings.DEFAULT);
        set(config, "plugin", plugin);
        set(config, "configUpdater", true);
        set(config, "fileName", "messages.yml");
        set(config, "config", document);
        doThrow(new UnsupportedOperationException("private detail")).when(document).update();
        config.update();
        verify(logger).warning("Failed to update messages.yml (UnsupportedOperationException)");
        verify(logger, never()).info(anyString());
    }
    @Test void inMemoryNewerDocumentHasNullSafeLabel(@TempDir Path folder) throws Exception {
        Fixture fixture = fixture(folder, "version: 15\n", "version: 13\n", false, true);
        set(fixture.config(), "fileName", null);
        fixture.config().update();
        verify(fixture.logger()).info("Preserving configuration: file version 15 is newer than bundled defaults 13; automatic downgrade disabled.");
    }
    private static Fixture fixture(Path folder, String contents, String defaults, boolean downgrade, boolean versioning) throws Exception {
        Path file = folder.resolve("messages.yml");
        Files.deleteIfExists(file);
        UpdaterSettings.Builder settings = UpdaterSettings.builder().setKeepAll(true).setEnableDowngrading(downgrade);
        if (versioning) settings.setVersioning(new BasicVersioning("version"));
        YamlDocument document = spy(YamlDocument.create(file.toFile(), defaults == null ? null :
                new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8)),
                GeneralSettings.builder().setUseDefaults(false).build(),
                LoaderSettings.builder().setCreateFileIfAbsent(false).build(), settings.build()));
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        try (ByteArrayInputStream input = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))) {
            document.reload(input);
        }
        clearInvocations(document);
        ConfigBase config = mock(ConfigBase.class, CALLS_REAL_METHODS);
        Plugin plugin = mock(Plugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        set(config, "plugin", plugin);
        set(config, "configUpdater", true);
        set(config, "fileName", "messages.yml");
        set(config, "config", document);
        return new Fixture(config, document, logger, file);
    }
    private record Fixture(ConfigBase config, YamlDocument document, Logger logger, Path file) {}

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = ConfigBase.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
