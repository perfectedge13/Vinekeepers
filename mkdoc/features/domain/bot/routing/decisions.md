# Decisions

# Selected decisions

- Routing is evaluated over a normalized event view so Discord and GitHub payloads can share one filter model.
- Discord mention activation is treated as routing input and may come from payload metadata or parsed `@mention` text.
- Bot runtime definition and tool policy are documented separately because routing selects bots before execution policy applies.

