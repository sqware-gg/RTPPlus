package dev.rtpplus.rtp;

import org.bukkit.Location;

public record RtpResult(boolean success, Location location, int attempts) {
    public static RtpResult success(Location location, int attempts) {
        return new RtpResult(true, location, attempts);
    }

    public static RtpResult failed(int attempts) {
        return new RtpResult(false, null, attempts);
    }
}
