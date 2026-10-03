package io.github.sanowo0390.anchoredfreecam;

import java.util.Locale;
import java.util.Map;

final class Messages {
    private static final Map<String, String> JA = Map.ofEntries(
            Map.entry("no-permission", "この操作を行う権限がありません。"),
            Map.entry("reloaded", "AnchoredFreecam の設定を再読み込みしました。"),
            Map.entry("current-range", "現在のFreecam範囲: {range} マス"),
            Map.entry("range-usage", "使い方: /{label} range <マス>"),
            Map.entry("range-number", "範囲は数値で指定してください。"),
            Map.entry("range-limits", "範囲は {min} ～ {max} マスで指定してください。"),
            Map.entry("range-set", "Freecam範囲を {range} マスに変更しました。"),
            Map.entry("current-language", "現在の言語: {language}"),
            Map.entry("language-usage", "使い方: /{label} language <ja|en>"),
            Map.entry("language-unsupported", "対応言語は ja / en です。"),
            Map.entry("language-set", "言語を {language} に変更しました。"),
            Map.entry("console-usage", "コンソールでは /freecam range、/freecam language、/freecam reload を使用できます。"),
            Map.entry("already-on", "FreecamはすでにONです。"),
            Map.entry("already-off", "FreecamはすでにOFFです。"),
            Map.entry("status", "Freecam: {state} / 半径 {range} マス"),
            Map.entry("usage", "使い方: /{label} [on|off|toggle|status|range|language|reload]"),
            Map.entry("state-on", "ON"),
            Map.entry("state-off", "OFF"),
            Map.entry("spectator-denied", "Spectatorではブロック衝突を維持できないため開始できません。"),
            Map.entry("vehicle-denied", "乗り物から降りてから使用してください。"),
            Map.entry("body-spawn-failed", "本体を生成できなかったためFreecamを開始できません。"),
            Map.entry("freecam-enabled", "Freecam ON — 本体を残したまま、開始地点から {range} マス以内を移動できます。"),
            Map.entry("freecam-disabled-return", "Freecam OFF — 本体の位置へ戻りました。"),
            Map.entry("freecam-disabled", "Freecamを終了しました。"),
            Map.entry("body-damaged", "本体がダメージを受けたためFreecamを終了しました。"),
            Map.entry("boundary-return", "Freecamの範囲は本体から {range} マスです。範囲内へ戻しました。")
    );

    private static final Map<String, String> EN = Map.ofEntries(
            Map.entry("no-permission", "You do not have permission to do that."),
            Map.entry("reloaded", "Reloaded the AnchoredFreecam configuration."),
            Map.entry("current-range", "Current freecam range: {range} blocks"),
            Map.entry("range-usage", "Usage: /{label} range <blocks>"),
            Map.entry("range-number", "The range must be a number."),
            Map.entry("range-limits", "The range must be between {min} and {max} blocks."),
            Map.entry("range-set", "Set the freecam range to {range} blocks."),
            Map.entry("current-language", "Current language: {language}"),
            Map.entry("language-usage", "Usage: /{label} language <ja|en>"),
            Map.entry("language-unsupported", "Supported languages are ja and en."),
            Map.entry("language-set", "Set the language to {language}."),
            Map.entry("console-usage", "From console, use /freecam range, /freecam language, or /freecam reload."),
            Map.entry("already-on", "Freecam is already ON."),
            Map.entry("already-off", "Freecam is already OFF."),
            Map.entry("status", "Freecam: {state} / radius {range} blocks"),
            Map.entry("usage", "Usage: /{label} [on|off|toggle|status|range|language|reload]"),
            Map.entry("state-on", "ON"),
            Map.entry("state-off", "OFF"),
            Map.entry("spectator-denied", "Freecam cannot start in Spectator because block collision cannot be preserved."),
            Map.entry("vehicle-denied", "Leave your vehicle before using freecam."),
            Map.entry("body-spawn-failed", "Freecam could not start because the anchored body could not be created."),
            Map.entry("freecam-enabled", "Freecam ON — your body remains anchored and the camera can move within {range} blocks."),
            Map.entry("freecam-disabled-return", "Freecam OFF — returned to your body."),
            Map.entry("freecam-disabled", "Freecam ended."),
            Map.entry("body-damaged", "Freecam ended because your anchored body took damage."),
            Map.entry("boundary-return", "Freecam range is {range} blocks from your body. Returned inside the boundary.")
    );

    private final AnchoredFreecamPlugin plugin;

    Messages(AnchoredFreecamPlugin plugin) {
        this.plugin = plugin;
    }

    String language() {
        return normalizeLanguage(plugin.getConfig().getString("language", "ja"));
    }

    boolean isSupported(String language) {
        if (language == null) {
            return false;
        }
        String normalized = language.toLowerCase(Locale.ROOT);
        return normalized.equals("ja") || normalized.equals("jp")
                || normalized.equals("en") || normalized.equals("english");
    }

    String normalizeLanguage(String language) {
        if (language == null) {
            return "ja";
        }
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "en", "english" -> "en";
            default -> "ja";
        };
    }

    String text(String key, String... replacements) {
        Map<String, String> table = language().equals("en") ? EN : JA;
        String value = table.getOrDefault(key, JA.getOrDefault(key, key));

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return value;
    }
}
