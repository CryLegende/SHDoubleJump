package org.Cry_Legende.sHDoubleJump;

import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * messages.yml : textes du plugin. Une clé absente du fichier du serveur prend la valeur du jar.
 */
final class Messages {

    static final String FILE = "messages.yml";

    private final JavaPlugin plugin;
    private volatile YamlConfiguration yaml = new YamlConfiguration();

    Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Lit messages.yml (copié depuis le jar s'il manque) sans rien remplacer en mémoire : une erreur de syntaxe est levée telle quelle.
     */
    YamlConfiguration read() throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists()) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file);
        YamlConfiguration defaults = jarDefaults();
        if (defaults != null) {
            loaded.setDefaults(defaults);
        }
        return loaded;
    }

    /**
     * Remplace les textes en mémoire par ceux d'une lecture réussie.
     */
    void apply(YamlConfiguration loaded) {
        this.yaml = loaded;
    }

    /**
     * Au démarrage : un fichier illisible ne bloque pas le plugin, les textes du jar servent.
     */
    void loadAtStartup() {
        try {
            apply(read());
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, FILE + " illisible, textes du jar utilisés : " + describe(e));
            YamlConfiguration fallback = new YamlConfiguration();
            YamlConfiguration defaults = jarDefaults();
            if (defaults != null) {
                fallback.setDefaults(defaults);
            }
            apply(fallback);
        }
    }

    private YamlConfiguration jarDefaults() {
        try (InputStream in = plugin.getResource(FILE)) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, FILE + " du jar illisible : " + e.getMessage());
            return null;
        }
    }

    /**
     * Texte de messages.yml, couleurs & traduites, puis variables remplacées (paires : nom, valeur).
     */
    String get(String key, String... pairs) {
        String text = yaml.getString(key);
        if (text == null) {
            text = key;
        }
        text = ChatColor.translateAlternateColorCodes('&', text);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return text;
    }

    /**
     * Lit un fichier YAML en mode strict : une erreur de syntaxe est levée au lieu d'être avalée comme le fait reloadConfig().
     */
    static YamlConfiguration loadStrict(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file);
        return loaded;
    }

    /**
     * Cause d'une erreur de lecture sur une seule ligne, sans la citation du fichier.
     */
    static String describe(Exception e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        text = text.replaceAll("\\s*\\R\\s*", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }
}
