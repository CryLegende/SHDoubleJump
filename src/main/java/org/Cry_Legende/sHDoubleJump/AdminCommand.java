package org.Cry_Legende.sHDoubleJump;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /shdoublejump reload | info [joueur] : utilisable depuis la console (info demande alors un joueur).
 */
final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_RELOAD = "shdoublejump.admin.reload";
    private static final String PERMISSION_INFO = "shdoublejump.admin.info";

    private final SHDoubleJump plugin;

    AdminCommand(SHDoubleJump plugin) {
        this.plugin = plugin;
    }

    /** Sous-commandes que l'expéditeur a le droit d'utiliser. */
    private List<String> allowed(CommandSender sender) {
        List<String> subs = new ArrayList<>();
        if (sender.hasPermission(PERMISSION_RELOAD)) {
            subs.add("reload");
        }
        if (sender.hasPermission(PERMISSION_INFO)) {
            subs.add("info");
        }
        return subs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        List<String> subs = allowed(sender);
        if (subs.isEmpty()) {
            sender.sendMessage(messages.get("pas-de-permission"));
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("reload") && args.length == 1 && subs.contains("reload")) {
            String error = plugin.reloadAll();
            if (error == null) {
                sender.sendMessage(messages.get("reload-ok"));
            } else {
                String[] parts = error.split(" : ", 2);
                sender.sendMessage(messages.get("reload-erreur", "{fichier}", parts[0], "{erreur}", parts.length > 1 ? parts[1] : ""));
            }
        } else if (sub.equals("info") && args.length <= 2 && subs.contains("info")) {
            Player target;
            if (args.length == 2) {
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(messages.get("joueur-introuvable", "{joueur}", args[1]));
                    return true;
                }
            } else if (sender instanceof Player self) {
                target = self;
            } else {
                sender.sendMessage(messages.get("console-joueur"));
                return true;
            }
            plugin.infoLines(target).forEach(sender::sendMessage);
        } else {
            sender.sendMessage(messages.get("usage-liste", "{sous-commandes}", String.join("|", subs)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            for (String sub : allowed(sender)) {
                if (sub.startsWith(start)) {
                    result.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info") && sender.hasPermission(PERMISSION_INFO)) {
            String start = args[1].toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(start)) {
                    result.add(online.getName());
                }
            }
        }
        return result;
    }
}
