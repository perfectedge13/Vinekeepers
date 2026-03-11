# Change log

# Entries

## 2026-03-10

- **Lifecycle room (Phase 1):** The `create_channel` workflow action (used by Luna lifecycle room) delegates to gateway `createTextChannel(guildId, channelName)`; the action normalizes channel name to Discord-safe format before calling the gateway and returns sentinel `CHANNEL_CREATE_FAILED` on failure. Documented in workflow-steps and cursor-gathering; gateway contract unchanged.

## 2026-03-09

- **Discord intake initial components:** Gateway contract (`DiscordGateway`), JDA-backed gateway (`JdaDiscordGateway`), and reply sink (`DiscordAppReplySink`) updates for receive/send and interaction lifecycle; author (authorId, author) in payload for routing; adapter acks or defers within platform window; optional components on channel send and follow-up/update.
- **Author in payload for routing:** JdaDiscordGateway supplies author (authorId, author) in the internal event payload so routing can apply `discordAuthors` filter and NormalizedEventContext can expose actorId/actorUsername for matching.

## 2026-03-08

- **AppReplySink and rich intents:** Discord connector implements `AppReplySink` with lifecycle operations (respondImmediately, sendFollowUp, updateMessage, openModal); adapter acks or defers interactions within the platform window and may auto-defer when sync reply is not safely possible. Intents (PresentChoices, ConfirmAction, etc.) render to Discord with fallback to text. Engine delivers replies via sink registry by sourceId prefix.

## 2026-03-07

- The Discord reply path is now documented as part of the engine workflow and reasoner loop, including replies that originate from configured conversational workflows and are delivered back through `DiscordReplySender`.
- Discord docs now note that `DiscordEventSource` carries mention metadata so routing can match bot mentions such as `@Luna` case-insensitively from connector payloads or normalized text.
- Connector docs now describe the JDA-backed receive/send path, including mention metadata propagation and reply behavior that targets the originating message when possible.

## 2026-03-06

Implementation updates: DiscordReplySender.java, DiscordEventSource.java; tests DiscordEventSourceTest modified. Engine delivers workflow replies to Discord via reply path when source is Discord.

## Luna reply path

Engine sends workflow replies (e.g. Luna bot) back to Discord via DiscordReplySender; DiscordEventSource implements the interface and is set as the engine's reply sender when Discord is enabled.

