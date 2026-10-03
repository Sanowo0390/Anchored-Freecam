package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompatibilityTest {
    @TempDir Path folder;

    @Test
    void customLanguageFallbackAndReloadStillWork() throws Exception {
        Files.createDirectories(folder.resolve("lang"));
        Files.writeString(folder.resolve("lang/ja.yml"), "fallback: '既定'\n");
        Files.writeString(folder.resolve("lang/en.yml"), "fallback: 'default'\n");
        Files.writeString(folder.resolve("lang/custom.yml"), "welcome: '範囲 {range}'\n");
        AnchoredFreecamPlugin plugin = mock(AnchoredFreecamPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("language", "custom");
        config.set("fallback-language", "ja");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        Messages messages = new Messages(plugin);
        assertEquals("範囲 15", messages.text("welcome", "range", "15"));
        assertEquals("既定", messages.text("fallback"));
        assertTrue(messages.availableLanguages().contains("custom"));
        Files.writeString(folder.resolve("lang/custom.yml"), "welcome: '変更 {range}'\n");
        messages.reload();
        assertEquals("変更 15", messages.text("welcome", "range", "15"));
    }

    @Test
    void permissionChecksStillGateFreecamAndAdministration() {
        AnchoredFreecamPlugin plugin = mock(AnchoredFreecamPlugin.class);
        FreecamManager manager = mock(FreecamManager.class);
        Messages messages = mock(Messages.class);
        when(messages.text("no-permission")).thenReturn("denied");
        FreecamCommand executor = new FreecamCommand(plugin, manager, messages);
        Player player = mock(Player.class);
        Command command = mock(Command.class);
        for (String action : new String[]{"on", "range", "language", "reload"}) {
            executor.onCommand(player, command, "freecam", new String[]{action});
        }
        verifyNoInteractions(manager);
        verify(plugin, never()).reloadConfig();
        when(player.hasPermission("anchoredfreecam.use")).thenReturn(true);
        executor.onCommand(player, command, "freecam", new String[]{"on"});
        verify(manager).start(player);
    }

    @Test
    void packagedDefaultsAndPermissionNodesRemainCompatible() {
        YamlConfiguration defaults = resource("config.yml");
        assertEquals(15, defaults.getDouble("max-distance-blocks"));
        assertTrue(defaults.getBoolean("show-body-nameplate"));
        assertTrue(defaults.getBoolean("hide-camera-player-from-others"));
        assertFalse(defaults.getBoolean("debug-water"));
        YamlConfiguration descriptor = resource("plugin.yml");
        assertEquals("26.2", descriptor.getString("api-version"));
        for (String node : new String[]{"use", "range", "language", "reload"}) {
            assertTrue(descriptor.getBoolean("permissions.anchoredfreecam.admin.children.anchoredfreecam." + node));
        }
    }

    private YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream(name), StandardCharsets.UTF_8));
    }
}
