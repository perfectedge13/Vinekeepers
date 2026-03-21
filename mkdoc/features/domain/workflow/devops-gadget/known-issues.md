# Known issues

- Long ansible output can hit Discord rate limits; runner emits selected lines only (TASK, PLAY, fatal, ok/changed summaries).
- Very large images: the runtime stage includes the full **ansible** apt package for Gadget deploy; trim with a custom image if size matters.
