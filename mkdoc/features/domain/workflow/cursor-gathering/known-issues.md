# Known issues

# Current limitations

- This flow depends on external Cursor Cloud and repository integrations that are not fully exercisable in isolated unit tests.
- The documented gathering path is specialized to the shipped Luna configuration rather than a general-purpose multi-bot pattern.
- Run tracking is intentionally in-memory for v1, so active Cursor runs and pending progress notifications are not recovered after an app restart.

