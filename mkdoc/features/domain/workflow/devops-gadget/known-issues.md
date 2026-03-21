# Known issues

- Long ansible output can hit Discord rate limits; runner emits selected lines only (TASK, PLAY, fatal, ok/changed summaries).
- Base Docker image does not install `ansible` by default; use a derived image or host wrapper script if needed.
