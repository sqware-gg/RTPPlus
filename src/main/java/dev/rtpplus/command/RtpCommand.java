package dev.rtpplus.command;

import dev.rtpplus.rtp.RtpService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class RtpCommand implements CommandExecutor, TabCompleter {
    private final RtpService service;

    public RtpCommand(RtpService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only", Map.of());
            return true;
        }
        if (!player.hasPermission("rtpplus.use")) {
            service.send(player, "no-permission", Map.of());
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
            if (!player.hasPermission("rtpplus.cancel")) {
                service.send(player, "no-permission", Map.of());
                return true;
            }
            if (!service.cancel(player.getUniqueId(), "cancelled")) {
                service.send(player, "none-running", Map.of());
            }
            return true;
        }
        if (args.length > 1 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) {
            service.send(player, "usage", Map.of());
            return true;
        }
        String world = args.length == 1 ? args[0] : null;
        if (world != null && !player.hasPermission("rtpplus.world")) {
            service.send(player, "no-permission", Map.of());
            return true;
        }
        service.request(player, world, false);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("rtpplus.use")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            values.add("cancel");
            values.addAll(service.config().worlds().values().stream()
                    .filter(settings -> settings.enabled())
                    .map(settings -> settings.worldName().toLowerCase(Locale.ROOT))
                    .toList());
            return filter(values, args[0]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
