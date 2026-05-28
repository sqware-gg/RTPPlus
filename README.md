# RTPPlus

RTPPlus is a BetterRTP-style random teleport plugin for Paper servers. It uses bounded request queues, warmups, cooldowns, Paper async chunk loading, world-border aware searches, and strict safe-location checks.

Use it when you want `/rtp`, `/wild`, or `/randomtp` without expensive unbounded location searches.

## Features

- Random teleport by world.
- `/wild`, `/wilderness`, and `/randomtp` aliases.
- Warmups, cooldowns, and bypass permissions.
- Cancelable pending teleports.
- Bounded request queues to avoid server spikes.
- Paper async chunk loading.
- World-border aware candidate generation.
- Safe-location checks before teleporting.
- Admin status, reload, cancel, cooldown clear, and test commands.

## Requirements

- Paper `26.1.2+`
- Java `25+`
- Maven

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

Aliases: `/wilderness`, `/randomtp`, `/rtpadmin`

## Permissions

```text
rtpplus.use             - use /rtp
rtpplus.world           - choose a configured RTP world
rtpplus.cancel          - cancel your own pending request
rtpplus.cooldown.bypass - bypass cooldowns
rtpplus.warmup.bypass   - bypass warmups
rtpplus.admin           - admin commands
```

## Build

```powershell
mvn package
```

The jar is written to `target/RTPPlus-0.1.0.jar`.

## Support

- Website: https://sqware.gg
- Discord: https://discord.sqware.gg
