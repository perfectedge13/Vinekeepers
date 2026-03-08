# Change log

# Entries

## 2026-03-07

- The Discord reply path is now documented as part of the engine workflow and reasoner loop, including replies that originate from configured conversational workflows and are delivered back through `DiscordReplySender`.
- Discord docs now note that `DiscordEventSource` carries mention metadata so routing can match bot mentions such as `@Luna` case-insensitively from connector payloads or normalized text.
- Connector docs now describe the JDA-backed receive/send path, including mention metadata propagation and reply behavior that targets the originating message when possible.

## 2026-03-06

Implementation updates: DiscordReplySender.java, DiscordEventSource.java; tests DiscordEventSourceTest modified. Engine delivers workflow replies to Discord via reply path when source is Discord.

## Luna reply path

Engine sends workflow replies (e.g. Luna bot) back to Discord via DiscordReplySender; DiscordEventSource implements the interface and is set as the engine's reply sender when Discord is enabled.

