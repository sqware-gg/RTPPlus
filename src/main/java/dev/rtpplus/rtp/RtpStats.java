package dev.rtpplus.rtp;

public final class RtpStats {
    private long searches;
    private long success;
    private long failed;

    public long searches() {
        return searches;
    }

    public long success() {
        return success;
    }

    public long failed() {
        return failed;
    }

    public void searched() {
        searches++;
    }

    public void recordSuccess() {
        success++;
    }

    public void recordFailed() {
        failed++;
    }
}
