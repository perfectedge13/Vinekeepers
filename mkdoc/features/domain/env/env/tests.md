# Tests

# Coverage

Unit test: EnvLoaderTest verifies .env loading and Env.get behavior.
Manual coverage: the health endpoint can be verified by running the app with `HEALTH_PORT` set and checking `GET /health` returns `OK`.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-ENV-LOADER | EnvLoader loads and Env reads | com.vinekeepers.env.EnvLoaderTest | (various) | Verify .env loading and Env.get behavior |
| MANUAL-ENV-HEALTH-SERVER | Health server responds on configured port | (manual) | (manual) | Verify `HEALTH_PORT` starts the listener and `GET /health` returns `OK` |

