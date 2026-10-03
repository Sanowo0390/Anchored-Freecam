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
    private final Messages messages;

    FreecamCommand(AnchoredFreecamPlugin plugin, FreecamManager manager, Messages messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
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
            messages.reload();
            manager.refreshVisibility();
            manager.enforceCurrentRange();

            sender.sendMessage(Component.text(
                    messages.text("reloaded"),
                    NamedTextColor.GREEN));
            return true;
        }

        if (sub.equals("language") || sub.equals("lang")) {
            if (!sender.hasPermission("anchoredfreecam.language")) {
                noPermission(sender);
                return true;
            }

            if (args.length == 1) {
                sender.sendMessage(Component.text(
                        messages.text("current-language", "language", messages.language()),
                        NamedTextColor.AQUA));
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(Component.text(
                        messages.text("language-usage", "label", label),
                        NamedTextColor.YELLOW));
                return true;
            }

            String language = messages.normalizeLanguageId(args[1]);
            if (language == null || !messages.isSupported(language)) {
                sender.sendMessage(Component.text(
                        messages.text(
                                "language-unsupported",
                                "language", String.valueOf(args[1]),
                                "languages", String.join(", ", messages.availableLanguages())),
                        NamedTextColor.RED));
                return true;
            }
            plugin.getConfig().set("language", language);
            plugin.saveConfig();
            messages.reload();

            sender.sendMessage(Component.text(
                    messages.text("language-set", "language", language),
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
                        messages.text(
                                "current-range",
                                "range", format(manager.getMaxDistance())),
                        NamedTextColor.AQUA));
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(Component.text(
                        messages.text("range-usage", "label", label),
                        NamedTextColor.YELLOW));
                return true;
            }

            final double range;
            try {
                range = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text(
                        messages.text("range-number"),
                        NamedTextColor.RED));
                return true;
            }

            if (!Double.isFinite(range) || range < MIN_RANGE || range > MAX_RANGE) {
                sender.sendMessage(Component.text(
                        messages.text(
                                "range-limits",
                                "min", format(MIN_RANGE),
                                "max", format(MAX_RANGE)),
                        NamedTextColor.RED));
                return true;
            }

            plugin.getConfig().set("max-distance-blocks", range);
            plugin.saveConfig();
            manager.enforceCurrentRange();

            sender.sendMessage(Component.text(
                    messages.text("range-set", "range", format(range)),
                    NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    messages.text("console-usage"),
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
                            messages.text("already-on"),
                            NamedTextColor.YELLOW));
                } else {
                    manager.start(player);
                }
            }
            case "off" -> {
                if (!manager.stop(player, true, true)) {
                    player.sendMessage(Component.text(
                            messages.text("already-off"),
                            NamedTextColor.YELLOW));
                }
            }
            case "status" -> {
                boolean active = manager.isActive(player);
                player.sendMessage(Component.text(
                        messages.text(
                                "status",
                                "state", messages.text(active ? "state-on" : "state-off"),
                                "range", format(manager.getMaxDistance())),
                        active ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
            default -> player.sendMessage(Component.text(
                    messages.text("usage", "label", label),
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
            if (sender.hasPermission("anchoredfreecam.language")) {
                candidates.add("language");
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
            return List.of("20", "10", "30", "50");
        }

        if (args.length == 2
                && (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang"))
                && sender.hasPermission("anchoredfreecam.language")) {
            return messages.availableLanguages();
        }

        return List.of();
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage(Component.text(
                messages.text("no-permission"),
                NamedTextColor.RED));
    }

    private String format(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }
}
