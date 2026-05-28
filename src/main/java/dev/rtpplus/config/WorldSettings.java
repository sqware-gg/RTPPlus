package dev.rtpplus.config;

public record WorldSettings(
        String worldName,
        boolean enabled,
        String centerMode,
        int centerX,
        int centerZ,
        int minRadius,
        int maxRadius,
        boolean respectWorldBorder,
        boolean squareSearch
) {
}
