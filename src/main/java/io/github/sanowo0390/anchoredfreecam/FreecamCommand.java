package io.github.sanowo0390.anchoredfreecam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FreecamCommand implements CommandExecutor, TabCompleter {
    private final AnchoredFreecamPlugin plugin;
    private final FreecamManager manager;

    FreecamCommand(AnchoredFreecamPlugin plugin, FreecamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("anchoredfreecam.reload")) {
                sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
                return true;
            }
            plugin.reloadConfig();
            manager.refreshVisibility();
            sender.sendMessage(Component.text("AnchoredFreecam の設定を再読み込みしました。", NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("このコマンドはプレイヤーのみ使用できます。", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("anchoredfreecam.use")) {
            player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }

        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "toggle" -> {
                if (manager.isActive(player)) {
                    manager.stop(player, true, true);
                } else {
                    manager.start(player);
                }
            }
            case "on" -> {
                if (manager.isActive(player)) {
                    player.sendMessage(Component.text("FreecamはすでにONです。", NamedTextColor.YELLOW));
                } else {
                    manager.start(player);
                }
            }
            case "off" -> {
                if (!manager.stop(player, true, true)) {
                    player.sendMessage(Component.text("FreecamはすでにOFFです。", NamedTextColor.YELLOW));
                }
            }
            case "status" -> player.sendMessage(Component.text(
                    "Freecam: " + (manager.isActive(player) ? "ON" : "OFF")
                            + " / 半径 " + manager.getMaxDistance(),
                    manager.isActive(player) ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            default -> player.sendMessage(Component.text(
                    "使い方: /" + label + " [on|off|toggle|status|reload]",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>(List.of("on", "off", "toggle", "status"));
        if (sender.hasPermission("anchoredfreecam.reload")) {
            candidates.add("reload");
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
