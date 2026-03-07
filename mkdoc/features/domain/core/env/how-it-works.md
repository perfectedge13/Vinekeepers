# How it works

# Overview

The application calls EnvLoader.load(".env") before bootstrap. The loader reads the file and sets system properties. Env.get(key, default) reads from system properties or returns the default.

# Flow

1. VinekeepersApp starts; calls EnvLoader.load(".env").
2. EnvLoader parses .env and sets each key=value into system properties.
3. Code uses Env.get(key, default) for configuration access.

# Inputs and outputs

- **Inputs:** .env file at project root (optional). Keys and defaults for Env.get.
- **Outputs:** System properties populated; Env.get returns values or defaults.
