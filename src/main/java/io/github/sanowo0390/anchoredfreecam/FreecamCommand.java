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
    private static final double MIN_RANGE = 0.1D;
    private static final double MAX_RANGE = 256.0D;

    private final AnchoredFreecamPlugin plugin;
    private final FreecamManager manager;

    FreecamCommand(AnchoredFreecamPlugin plugin, FreecamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("anchoredfreecam.reload")) {
                noPermission(sender);
                return true;
            }

            plugin.reloadConfig();
            manager.refreshVisibility();
            sender.sendMessage(Component.text(
                    "AnchoredFreecam の設定を再読み込みしました。",
                    NamedTextColor.GREEN));
            return true;
        }

        if (sub.equals("range")) {
            if (!sender.hasPermission("anchoredfreecam.range")) {
                noPermission(sender);
                return true;
            }

            if (args.length == 1) {
                sender.sendMessage(Component.text(
                        "現在のFreecam範囲: " + format(manager.getMaxDistance()) + " マス",
                        NamedTextColor.AQUA));
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(Component.text(
                        "使い方: /" + label + " range <マス>",
                        NamedTextColor.YELLOW));
                return true;
            }

            final double range;
            try {
                range = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text(
                        "範囲は数値で指定してください。",
                        NamedTextColor.RED));
                return true;
            }

            if (!Double.isFinite(range) || range < MIN_RANGE || range > MAX_RANGE) {
                sender.sendMessage(Component.text(
                        "範囲は " + format(MIN_RANGE) + " ～ " + format(MAX_RANGE) + " マスで指定してください。",
                        NamedTextColor.RED));
                return true;
            }

            plugin.getConfig().set("max-distance-blocks", range);
            plugin.saveConfig();

            sender.sendMessage(Component.text(
                    "Freecam範囲を " + format(range) + " マスに変更しました。",
                    NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "プレイヤー以外は /freecam range または /freecam reload を使用できます。",
                    NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("anchoredfreecam.use")) {
            noPermission(player);
            return true;
        }

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
                    player.sendMessage(Component.text(
                            "FreecamはすでにONです。",
                            NamedTextColor.YELLOW));
                } else {
                    manager.start(player);
                }
            }
            case "off" -> {
                if (!manager.stop(player, true, true)) {
                    player.sendMessage(Component.text(
                            "FreecamはすでにOFFです。",
                            NamedTextColor.YELLOW));
                }
            }
            case "status" -> player.sendMessage(Component.text(
                    "Freecam: " + (manager.isActive(player) ? "ON" : "OFF")
                            + " / 半径 " + format(manager.getMaxDistance()) + " マス",
                    manager.isActive(player) ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            default -> player.sendMessage(Component.text(
                    "使い方: /" + label + " [on|off|toggle|status|range|reload]",
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
        if (args.length == 1) {
            List<String> candidates = new ArrayList<>();

            if (sender.hasPermission("anchoredfreecam.use")) {
                candidates.addAll(List.of("on", "off", "toggle", "status"));
            }
            if (sender.hasPermission("anchoredfreecam.range")) {
                candidates.add("range");
            }
            if (sender.hasPermission("anchoredfreecam.reload")) {
                candidates.add("reload");
            }

            String prefix = args[0].toLowerCase(Locale.ROOT);
            return candidates.stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("range")
                && sender.hasPermission("anchoredfreecam.range")) {
            return List.of("5", "10", "15", "20");
        }

        return List.of();
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Component.text(
                "この操作を行う権限がありません。",
                NamedTextColor.RED));
    }

    private String format(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }
}
