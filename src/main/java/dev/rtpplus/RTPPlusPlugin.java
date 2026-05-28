package dev.rtpplus;

import dev.rtpplus.command.RtpCommand;
import dev.rtpplus.command.RtpPlusCommand;
import dev.rtpplus.config.ConfigReferenceWriter;
import dev.rtpplus.config.RtpPlusConfig;
import dev.rtpplus.listener.RtpListener;
import dev.rtpplus.rtp.RtpService;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RTPPlusPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 31620;

    private RtpPlusConfig rtpConfig;
    private RtpService rtpService;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_PLUGIN_ID);
        ConfigReferenceWriter.saveDefaultAndReferenceIfNeeded(this);

        rtpConfig = new RtpPlusConfig(this);
        rtpService = new RtpService(this, rtpConfig);

        getServer().getPluginManager().registerEvents(new RtpListener(rtpService), this);
        registerCommands();
        getLogger().info("Loaded " + rtpConfig.worlds().values().stream().filter(settings -> settings.enabled()).count()
                + " enabled RTP worlds.");
    }

    @Override
    public void onDisable() {
        if (rtpService != null) {
            rtpService.shutdown();
        }
    }

    private void reloadPlugin() {
        rtpConfig.reload();
        rtpService.reload(rtpConfig);
    }

    private void registerCommands() {
        RtpCommand rtpCommand = new RtpCommand(rtpService);
        PluginCommand rtp = getCommand("rtp");
        if (rtp != null) {
            rtp.setExecutor(rtpCommand);
            rtp.setTabCompleter(rtpCommand);
        }

        RtpPlusCommand adminCommand = new RtpPlusCommand(this, rtpService, this::reloadPlugin);
        PluginCommand admin = getCommand("rtpplus");
        if (admin != null) {
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(adminCommand);
        }
    }
}
