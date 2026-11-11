package org.Cry_Legende.sHDoubleJump;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldedit.bukkit.BukkitAdapter;

public class SHDoubleJump extends JavaPlugin implements Listener {

    /**
     * Metadata posee par SHTempFly tant que c'est lui qui accorde le vol au joueur.
     * Dans ce cas le joueur peut voler pour de vrai : le double saut se met en retrait,
     * il ne touche pas a allowFlight et n'annule pas l'evenement de decollage.
     */
    private static final String TEMPFLY_METADATA = "shtempfly.granted";

    /**
     * Metadata posee par SHParkour tant que le joueur est sur un parcours.
     * Le double saut est alors completement desactive : sans elle, un double saut
     * involontaire declenchait un PlayerToggleFlightEvent et SHParkour expulsait le joueur.
     */
    private static final String PARKOUR_METADATA = "shparkour.active";

    // Pour gérer le délai entre les sauts
    private HashMap<UUID, Long> lastDoubleJumpTime = new HashMap<>();
    // Pour suivre si le plugin a activé le vol (permettant de sauter)
    private HashMap<UUID, Boolean> doubleJumpEnabled = new HashMap<>();
    // Pour compter le nombre de sauts effectués en l'air
    private HashMap<UUID, Integer> jumpCount = new HashMap<>();

    // Configuration (relue par /shdoublejump reload, lue par les événements des joueurs)
    private volatile List<String> worldList;
    private volatile String worldsMode; // "whitelist" ou "blacklist"
    private volatile List<String> wgZoneList;
    private volatile String wgZonesMode; // "whitelist" ou "blacklist"
    private volatile double jumpPower;
    private volatile long jumpDelay; // en millisecondes
    private volatile String soundName;
    private volatile float soundVolume;
    private volatile float soundPitch;
    private volatile String particleName;
    private volatile int particleCount;

    private WorldGuardPlugin wgPlugin;
    private final String BYPASS_PERMISSION = "shdoublejump.bypass"; // Ajout de la constante pour la permission
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        messages.loadAtStartup();
        loadConfiguration();

        // Récupérer WorldGuard si présent
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            wgPlugin = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");
        } else {
            getLogger().warning("WorldGuard non trouvé, la liste de zones ne sera pas appliquée.");
        }

        if (Bukkit.getPluginManager().getPlugin("SHTempFly") != null) {
            getLogger().info("SHTempFly détecté : le double saut se met en retrait quand le fly est accordé.");
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("shdoublejump");
        if (command != null) {
            AdminCommand admin = new AdminCommand(this);
            command.setExecutor(admin);
            command.setTabCompleter(admin);
        }
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (doubleJumpEnabled.getOrDefault(player.getUniqueId(), false)) {
                releaseFlight(player);
            }
        }
    }

    private void loadConfiguration() {
        FileConfiguration config = getConfig();
        worldList = config.getStringList("worlds");
        worldsMode = config.getString("worlds-mode", "whitelist").toLowerCase();
        wgZoneList = config.getStringList("worldguard-zones");
        wgZonesMode = config.getString("worldguard-zones-mode", "whitelist").toLowerCase();
        jumpPower = config.getDouble("jump.power", 1.0);
        jumpDelay = config.getLong("jump.delay", 0);
        soundName = config.getString("jump.sound.name", "ENTITY_PLAYER_LEVELUP");
        soundVolume = (float) config.getDouble("jump.sound.volume", 1.0);
        soundPitch = (float) config.getDouble("jump.sound.pitch", 1.0);
        particleName = config.getString("jump.particle.name", "CLOUD");
        particleCount = config.getInt("jump.particle.count", 10);
    }

    Messages messages() {
        return messages;
    }

    /**
     * Relit config.yml et messages.yml. Renvoie null si tout est lu, sinon « fichier : erreur » : l'ancienne
     * configuration reste alors en mémoire. Les joueurs connectés sont réévalués avec les nouveaux mondes et zones.
     */
    String reloadAll() {
        File config = new File(getDataFolder(), "config.yml");
        if (!config.exists()) {
            saveDefaultConfig();
        }
        try {
            Messages.loadStrict(config);
        } catch (IOException | InvalidConfigurationException e) {
            return "config.yml : " + Messages.describe(e);
        }
        YamlConfiguration loadedMessages;
        try {
            loadedMessages = messages.read();
        } catch (IOException | InvalidConfigurationException e) {
            return Messages.FILE + " : " + Messages.describe(e);
        }
        reloadConfig();
        messages.apply(loadedMessages);
        loadConfiguration();
        for (Player player : Bukkit.getOnlinePlayers()) {
            reevaluate(player);
        }
        return null;
    }

    /**
     * Rend ou pose le vol d'un joueur connecté d'après les réglages courants. Le vol n'est posé qu'au sol, comme le
     * fait le déplacement ; l'atterrissage suivant s'en charge sinon.
     */
    private void reevaluate(Player player) {
        if (player.hasPermission(BYPASS_PERMISSION) || !isAllowedLocation(player.getLocation())) {
            releaseFlight(player);
        } else if (player.isOnGround()) {
            grantFlight(player);
        }
    }

    /**
     * Lignes de /shdoublejump info : ce qui décide du double saut d'un joueur.
     */
    List<String> infoLines(Player target) {
        List<String> lines = new ArrayList<>();
        lines.add(messages.get("info-titre", "{joueur}", target.getName()));
        Location location = target.getLocation();
        String worldName = location.getWorld().getName();
        lines.add(messages.get("info-monde", "{monde}", worldName,
            "{etat}", messages.get(isAllowedWorld(worldName) ? "etat-autorise" : "etat-refuse")));
        ApplicableRegionSet regions = regionsAt(location);
        if (regions == null) {
            lines.add(messages.get("info-regions-non-verifiees"));
        } else {
            List<String> ids = new ArrayList<>();
            for (ProtectedRegion region : regions) {
                ids.add(region.getId());
            }
            lines.add(messages.get("info-regions", "{regions}", ids.isEmpty() ? messages.get("aucune") : String.join(", ", ids),
                "{etat}", messages.get(isAllowedZone(regions) ? "etat-autorise" : "etat-refuse")));
        }
        lines.add(messages.get("info-sauts", "{sauts}",
            target.hasPermission("shdoublejump.infinite") ? messages.get("illimite") : String.valueOf(getAllowedJumps(target))));
        lines.add(messages.get("info-bypass", "{etat}", messages.get(target.hasPermission(BYPASS_PERMISSION) ? "oui" : "non")));
        String cause = null;
        if (jumpBlocked(target)) {
            cause = messages.get("cause-parcours");
        } else if (target.getGameMode() == GameMode.CREATIVE) {
            cause = messages.get("cause-creatif");
        } else if (target.getGameMode() == GameMode.SPECTATOR) {
            cause = messages.get("cause-spectateur");
        } else if (target.hasMetadata(TEMPFLY_METADATA)) {
            cause = messages.get("cause-tempfly");
        }
        lines.add(cause == null ? messages.get("info-vol-libre") : messages.get("info-vol-ailleurs", "{cause}", cause));
        lines.add(messages.get("info-arme", "{etat}", messages.get(doubleJumpEnabled.getOrDefault(target.getUniqueId(), false) ? "oui" : "non")));
        return lines;
    }

    /**
     * Régions WorldGuard à une position, ou null si WorldGuard est absent, si aucune zone n'est configurée ou si le monde n'a pas de gestionnaire.
     */
    private ApplicableRegionSet regionsAt(Location location) {
        if (wgPlugin == null || wgZoneList.isEmpty()) {
            return null;
        }
        com.sk89q.worldedit.world.World wgWorld = BukkitAdapter.adapt(location.getWorld());
        if (wgWorld == null) {
            return null;
        }
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(wgWorld);
        if (regionManager == null) {
            return null;
        }
        return regionManager.getApplicableRegions(BlockVector3.at(location.getX(), location.getY(), location.getZ()));
    }

    /**
     * True si le vol du joueur appartient à quelqu'un d'autre : le mode de jeu
     * (créatif / spectateur) ou SHTempFly. Le double saut n'y touche jamais.
     */
    private boolean flightOwnedByOther(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return true;
        }
        return player.hasMetadata(TEMPFLY_METADATA);
    }

    /**
     * True si une activité en cours interdit le double saut (parcours SHParkour...).
     */
    private boolean jumpBlocked(Player player) {
        return player.hasMetadata(PARKOUR_METADATA);
    }

    /**
     * Rend le allowFlight que le double saut avait posé, sans écraser celui d'un autre plugin.
     */
    private void releaseFlight(Player player) {
        UUID uuid = player.getUniqueId();
        if (doubleJumpEnabled.getOrDefault(uuid, false) && !flightOwnedByOther(player)) {
            player.setAllowFlight(false);
        }
        doubleJumpEnabled.put(uuid, false);
    }

    /**
     * Prépare le joueur pour un double saut, sauf si son vol est géré ailleurs.
     */
    private void grantFlight(Player player) {
        if (jumpBlocked(player)) {
            releaseFlight(player);
            return;
        }
        if (flightOwnedByOther(player)) {
            doubleJumpEnabled.put(player.getUniqueId(), false);
            return;
        }
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
        doubleJumpEnabled.put(player.getUniqueId(), true);
    }

    private int getAllowedJumps(Player player) {
        if (player.hasPermission("shdoublejump.infinite")) {
            return Integer.MAX_VALUE;
        }
        int allowed = 1;
        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String perm = permInfo.getPermission();
            if (perm.startsWith("shdoublejump.") && !perm.equals(BYPASS_PERMISSION) && !perm.equals("shdoublejump.infinite")) { // Exclure bypass et infinite ici
                String suffix = perm.substring("shdoublejump.".length());
                try {
                    int num = Integer.parseInt(suffix);
                    if (num > allowed) {
                        allowed = num;
                    }
                } catch (NumberFormatException e) {
                    // Ignorer
                }
            }
        }
        return allowed;
    }

    // Priorité LOW : le double saut décide avant que SHTempFly n'annule le décollage.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Parcours en cours : aucun double saut, et on rend le allowFlight qu'on avait posé.
        if (jumpBlocked(player)) {
            releaseFlight(player);
            return;
        }

        // Le vol est géré ailleurs (créatif, spectateur, SHTempFly) : on laisse passer.
        if (flightOwnedByOther(player)) {
            doubleJumpEnabled.put(uuid, false);
            return;
        }

        if (player.hasPermission(BYPASS_PERMISSION)) {
            releaseFlight(player);
            return;
        }

        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        if (!doubleJumpEnabled.getOrDefault(uuid, false)) {
            return;
        }

        if (!isAllowedLocation(player.getLocation())) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastDoubleJumpTime.containsKey(uuid)) {
            long elapsed = now - lastDoubleJumpTime.get(uuid);
            if (elapsed < jumpDelay) {
                event.setCancelled(true);
                return;
            }
        }

        int maxJumps = getAllowedJumps(player);
        int currentJumps = jumpCount.getOrDefault(uuid, 0);
        if (currentJumps >= maxJumps) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        Vector direction = player.getLocation().getDirection();
        direction.setY(jumpPower); // Appliquer la puissance configurée
        player.setVelocity(direction);

        Sound sound = sound(soundName);
        if (sound != null) {
            player.getWorld().playSound(player.getLocation(), sound, soundVolume, soundPitch);
        } else {
            getLogger().warning("Le son configuré n'existe pas: " + soundName);
        }

        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase());
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 0.1, 0), particleCount);
        } catch (IllegalArgumentException e) {
            getLogger().warning("La particule configurée n'existe pas: " + particleName);
        }

        lastDoubleJumpTime.put(uuid, now);
        jumpCount.put(uuid, currentJumps + 1);

        if (jumpCount.get(uuid) < maxJumps) {
            grantFlight(player);
        } else {
            releaseFlight(player);
        }
    }

    /** Son lu en config : ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup). */
    static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUND_EVENT.get(key);
        }
        try {
            return (Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // Simple rotation de la tête : rien n'a changé pour le double saut
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Player player = event.getPlayer();

        if (!player.isOnGround()) return;

        UUID uuid = player.getUniqueId();
        jumpCount.put(uuid, 0); // Réinitialiser le compteur de sauts (aussi pour un atterrissage dans le même bloc)

        // Même bloc et double saut déjà accordé : ni permission ni région WorldGuard à réévaluer
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()
            && doubleJumpEnabled.getOrDefault(uuid, false)) {
            return;
        }

        if (player.hasPermission(BYPASS_PERMISSION) || !isAllowedLocation(player.getLocation())) {
            releaseFlight(player);
            return;
        }
        grantFlight(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        jumpCount.put(player.getUniqueId(), 0);

        if (player.hasPermission(BYPASS_PERMISSION) || !isAllowedLocation(player.getLocation())) {
            releaseFlight(player);
            return;
        }
        grantFlight(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastDoubleJumpTime.remove(uuid);
        doubleJumpEnabled.remove(uuid);
        jumpCount.remove(uuid);
    }

    private boolean isAllowedLocation(Location location) {
        String worldName = location.getWorld().getName();
        if (!isAllowedWorld(worldName)) {
            return false;
        }
        if (wgPlugin != null && !wgZoneList.isEmpty()) {
            com.sk89q.worldedit.world.World wgWorld = BukkitAdapter.adapt(location.getWorld());
            if (wgWorld != null) {
                RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(wgWorld);
                if (regionManager != null) {
                    BlockVector3 position = BlockVector3.at(location.getX(), location.getY(), location.getZ());
                    ApplicableRegionSet regions = regionManager.getApplicableRegions(position);
                    return isAllowedZone(regions);
                }
            }
        }
        return true;
    }

    private boolean isAllowedWorld(String worldName) {
        if (worldsMode.equals("whitelist")) {
            return worldList.isEmpty() || worldList.contains(worldName);
        } else if (worldsMode.equals("blacklist")) {
            return !worldList.contains(worldName);
        }
        return false;
    }

    private boolean isAllowedZone(ApplicableRegionSet regions) {
        if (wgZonesMode.equals("whitelist")) {
            for (ProtectedRegion region : regions) {
                if (wgZoneList.contains(region.getId())) {
                    return true;
                }
            }
            // Si la whitelist n'est pas vide et qu'aucune région n'a matché, alors on refuse
            return wgZoneList.isEmpty(); // True si la liste est vide (pas de restriction de zone), false sinon
        } else if (wgZonesMode.equals("blacklist")) {
            for (ProtectedRegion region : regions) {
                if (wgZoneList.contains(region.getId())) {
                    return false;
                }
            }
            return true;
        }
        return false; // Devrait être true si wgZoneList est vide et mode whitelist, ou si pas de match en blacklist
    }
}
