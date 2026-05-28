package dev.rtpplus.rtp;

import dev.rtpplus.config.WorldSettings;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

public final class RtpRequest {
    private final UUID playerUuid;
    private final WorldSettings settings;
    private final Location startLocation;
    private final long createdAtMillis;
    private final boolean adminTest;
    private RtpStatus status = RtpStatus.QUEUED;
    private BukkitTask warmupTask;
    private int attempts;

    public RtpRequest(UUID playerUuid, WorldSettings settings, Location startLocation, long createdAtMillis, boolean adminTest) {
        this.playerUuid = playerUuid;
        this.settings = settings;
        this.startLocation = startLocation;
        this.createdAtMillis = createdAtMillis;
        this.adminTest = adminTest;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public WorldSettings settings() {
        return settings;
    }

    public Location startLocation() {
        return startLocation;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public boolean adminTest() {
        return adminTest;
    }

    public RtpStatus status() {
        return status;
    }

    public void status(RtpStatus status) {
        this.status = status;
    }

    public BukkitTask warmupTask() {
        return warmupTask;
    }

    public void warmupTask(BukkitTask warmupTask) {
        this.warmupTask = warmupTask;
    }

    public int attempts() {
        return attempts;
    }

    public void attempts(int attempts) {
        this.attempts = attempts;
    }
}
