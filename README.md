# RTPPlus

RTPPlus provides performance-focused random teleportation for Paper servers.

It uses bounded request queues, warmups, cooldowns, Paper async chunk loading, world-border aware candidate generation, and strict safe-location checks to avoid expensive unbounded searches.

## Commands

```text
/rtp [world]
/wild [world]
/rtp cancel

/rtpplus status
/rtpplus reload
/rtpplus cancel <player>
/rtpplus cooldown clear <player>
/rtpplus test [world]
```

## Permissions

```text
rtpplus.use             - use /rtp
rtpplus.world           - choose a configured RTP world
rtpplus.cancel          - cancel your own pending request
rtpplus.cooldown.bypass - bypass cooldowns
rtpplus.warmup.bypass   - bypass warmups
rtpplus.admin           - manage RTP+
```
