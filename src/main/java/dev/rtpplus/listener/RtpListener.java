package dev.rtpplus.listener;

import dev.rtpplus.rtp.RtpService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class RtpListener implements Listener {
    private final RtpService service;

    public RtpListener(RtpService service) {
        this.service = service;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null) {
            service.handleMove(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            service.handleDamage(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.cancel(event.getPlayer().getUniqueId(), null);
    }
}
