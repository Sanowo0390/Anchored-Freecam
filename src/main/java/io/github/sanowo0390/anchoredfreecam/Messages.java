package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Messages {
    private final AnchoredFreecamPlugin plugin;
    private final File langDirectory;
    private final Map<String, YamlConfiguration> cache = new HashMap<>();

    Messages(AnchoredFreecamPlugin plugin) {
        this.plugin = plugin;
        this.langDirectory = new File(plugin.getDataFolder(), "lang");
        reload();
    }

    void reload() {
        ensureDefaultLanguageFiles();
        cache.clear();
    }

    String language() {
        String configured = normalizeLanguageId(plugin.getConfig().getString("language", "ja"));
        if (configured != null && isSupported(configured)) {
            return configured;
        }
        return fallbackLanguage();
    }

    String fallbackLanguage() {
        String configured = normalizeLanguageId(plugin.getConfig().getString("fallback-language", "ja"));
        if (configured != null && isSupported(configured)) {
            return configured;
        }
        if (isSupported("ja")) {
            return "ja";
        }
        if (isSupported("en")) {
            return "en";
        }

        List<String> available = availableLanguages();
        return available.isEmpty() ? "ja" : available.getFirst();
    }

    boolean isSupported(String language) {
        String id = normalizeLanguageId(language);
        if (id == null) {
            return false;
        }
        return languageFile(id).isFile();
    }

    String normalizeLanguageId(String language) {
        if (language == null) {
            return null;
        }

        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".yml")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }

        if (normalized.isBlank() || !normalized.matches("[a-z0-9_-]+")) {
            return null;
        }
        return normalized;
    }

    List<String> availableLanguages() {
        ensureDefaultLanguageFiles();

        File[] files = langDirectory.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".yml"));

        if (files == null) {
            return List.of();
        }

        List<String> languages = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            String id = normalizeLanguageId(name.substring(0, name.length() - 4));
            if (id != null) {
                languages.add(id);
            }
        }

        return languages.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    String text(String key, String... replacements) {
        String current = language();
        String fallback = fallbackLanguage();

        String value = config(current).getString(key);
        if (value == null && !fallback.equals(current)) {
            value = config(fallback).getString(key);
        }
        if (value == null) {
            value = key;
        }

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return value;
    }

    private YamlConfiguration config(String language) {
        return cache.computeIfAbsent(language, id ->
                YamlConfiguration.loadConfiguration(languageFile(id)));
    }

    private File languageFile(String language) {
        return new File(langDirectory, language + ".yml");
    }

    private void ensureDefaultLanguageFiles() {
        if (!langDirectory.exists() && !langDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create language directory: " + langDirectory);
        }

        saveBundledLanguageIfMissing("ja");
        saveBundledLanguageIfMissing("en");
    }

    private void saveBundledLanguageIfMissing(String language) {
        File target = languageFile(language);
        if (target.isFile()) {
            return;
        }

        try {
            plugin.saveResource("lang/" + language + ".yml", false);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(
                    "Bundled language file is missing: lang/" + language + ".yml");
        }
    }
}
