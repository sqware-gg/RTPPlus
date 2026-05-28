package dev.rtpplus.util;

public final class DurationFormatter {
    private DurationFormatter() {
    }

    public static String compact(long millis) {
        if (millis <= 0L) {
            return "now";
        }
        long seconds = Math.max(1L, millis / 1000L);
        long minutes = seconds / 60L;
        seconds %= 60L;
        long hours = minutes / 60L;
        minutes %= 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
