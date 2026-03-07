# Change log

# Entries

## 2026-03-06

Implementation updates: DiscordReplySender.java, DiscordEventSource.java; tests DiscordEventSourceTest modified. Engine delivers workflow replies to Discord via reply path when source is Discord.

## Luna reply path

Engine sends workflow replies (e.g. Luna bot) back to Discord via DiscordReplySender; DiscordEventSource implements the interface and is set as the engine's reply sender when Discord is enabled.
