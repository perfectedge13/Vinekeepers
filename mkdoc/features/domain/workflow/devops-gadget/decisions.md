# Decisions

- **Async deploy:** Playbook runs off the event thread so the Discord gateway stays responsive (same pattern as CursorCloudRunMonitor).
- **sendAs gadget:** Progress must use Gadget’s token in multi-bot setups; default sender may be another bot.
- **Thread for status, channel for menus:** Keeps `discordChannels` filter simple (parent channel id only).
