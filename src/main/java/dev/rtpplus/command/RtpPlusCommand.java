package dev.rtpplus.command;

import dev.rtpplus.config.WorldSettings;
import dev.rtpplus.rtp.RtpStats;
import dev.rtpplus.rtp.RtpService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RtpPlusCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final RtpService service;
    private final Runnable reloadAction;

    public RtpPlusCommand(JavaPlugin plugin, RtpService service, Runnable reloadAction) {
        this.plugin = plugin;
        this.service = service;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rtpplus.admin")) {
            service.send(sender, "no-permission", Map.of());
            return true;
        }
        if (args.length == 0) {
            service.send(sender, "usage-admin", Map.of());
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status", "stats" -> status(sender);
            case "reload" -> {
                reloadAction.run();
                service.send(sender, "reloaded", Map.of());
            }
            case "cancel" -> cancel(sender, args);
            case "cooldown" -> cooldown(sender, args);
            case "test" -> test(sender, args);
            default -> service.send(sender, "usage-admin", Map.of());
        }
        return true;
    }

    private void status(CommandSender sender) {
        RtpStats stats = service.stats();
        service.send(sender, "status", Map.of(
                "active", Integer.toString(service.activeCount()),
                "queued", Integer.toString(service.queuedCount()),
                "searches", Long.toString(stats.searches()),
                "success", Long.toString(stats.success()),
                "failed", Long.toString(stats.failed())
        ));
    }

    private void cancel(CommandSender sender, String[] args) {
        if (args.length != 2) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            service.send(sender, "invalid-player", Map.of());
            return;
        }
        if (!service.cancel(target.getUniqueId(), "cancelled")) {
            service.send(sender, "none-running", Map.of());
        }
    }

    private void cooldown(CommandSender sender, String[] args) {
        if (args.length != 3 || !args[1].equalsIgnoreCase("clear")) {
            service.send(sender, "usage-admin", Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            service.send(sender, "invalid-player", Map.of());
            return;
        }
        service.clearCooldown(target.getUniqueId());
        service.send(sender, "cooldown-cleared", Map.of("player", target.getName()));
    }

    private void test(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return;
        }
        String world = args.length >= 2 ? args[1] : null;
        WorldSettings settings = world == null ? service.config().defaultWorld() : service.config().world(world);
        if (settings == null || !settings.enabled()) {
            service.send(player, "invalid-world", Map.of());
            return;
        }
        plugin.getLogger().info(player.getName() + " started an RTP test search for world " + settings.worldName());
        service.request(player, settings.worldName(), true);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rtpplus.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("status", "reload", "cancel", "cooldown", "test"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cooldown")) {
            return filter(List.of("clear"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cooldown") && args[1].equalsIgnoreCase("clear")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return filter(new ArrayList<>(service.config().worlds().keySet()), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase();
        return values.stream()
                .filter(value -> value.toLowerCase().startsWith(normalized))
                .toList();
    }
}
