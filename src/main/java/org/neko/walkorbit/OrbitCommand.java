package org.neko.walkorbit;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrbitCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "walkorbit.admin";
    private static final String MENU = "walkorbit.menu";

    private final WalkOrbit plugin;
    private final LocaleManager lm;

    public OrbitCommand(WalkOrbit plugin) {
        this.plugin = plugin;
        this.lm = plugin.getLocaleManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player self = sender instanceof Player p ? p : null;
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "menu", "gui" -> {
                if (self == null) {
                    sender.sendMessage(lm.getString(args.length == 0 ? "command.usage" : "command.player_only", null));
                    return true;
                }
                if (!self.hasPermission(MENU)) {
                    self.sendMessage(lm.getString("command.no_permission", self));
                    return true;
                }
                GuiManager.openMain(self, plugin, 0);
            }
            case "reload" -> {
                if (!checkAdmin(sender, self)) return true;
                plugin.reloadAll();
                sender.sendMessage(lm.getString("command.reloaded", self));
            }
            case "set" -> {
                if (!checkAdmin(sender, self)) return true;
                if (args.length < 3) {
                    sender.sendMessage(lm.getString("command.usage", self));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(lm.getString("command.player_not_found", self, Map.of("player", args[1])));
                    return true;
                }
                if (!plugin.getGroups().containsKey(args[2])) {
                    sender.sendMessage(lm.getString("command.group_not_found", self, Map.of("group", args[2])));
                    return true;
                }
                plugin.setSelectedGroup(target, args[2]);
                sender.sendMessage(lm.getString("command.set", self, Map.of("player", target.getName(), "group", args[2])));
            }
            case "clear" -> {
                if (!checkAdmin(sender, self)) return true;
                if (args.length < 2) {
                    sender.sendMessage(lm.getString("command.usage", self));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(lm.getString("command.player_not_found", self, Map.of("player", args[1])));
                    return true;
                }
                plugin.setSelectedGroup(target, null);
                sender.sendMessage(lm.getString("command.cleared", self, Map.of("player", target.getName())));
            }
            default -> sender.sendMessage(lm.getString("command.usage", self));
        }
        return true;
    }

    private boolean checkAdmin(CommandSender sender, Player self) {
        if (sender.hasPermission(ADMIN)) return true;
        sender.sendMessage(lm.getString("command.no_permission", self));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            if (sender.hasPermission(MENU)) options.add("menu");
            if (admin) options.addAll(List.of("set", "clear", "reload"));
        } else if (args.length == 2 && admin && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("clear"))) {
            return null; // player names
        } else if (args.length == 3 && admin && args[0].equalsIgnoreCase("set")) {
            options.addAll(plugin.getGroups().keySet());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
