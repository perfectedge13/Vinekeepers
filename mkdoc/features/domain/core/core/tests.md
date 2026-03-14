# Tests

# Coverage

REQ-CORE-001 and REQ-CORE-002 are validated by manual spec checks and unit tests for application entrypoint and Bootstrap.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| MANUAL-SPEC-VALIDATE | Validate spec index and registry | — | — | Ensure specs/specs.yml and registry exist and pass schema validation |
| UNIT-VINEKEEPERS-APP | VinekeepersAppTest | com.vinekeepers.VinekeepersAppTest | — | Verify application entrypoint and bootstrap |
| UNIT-BOOTSTRAP | BootstrapTest | com.vinekeepers.core.BootstrapTest | — | Verify Bootstrap wires engine, config, ConnectorRegistry, Discord adapter and registerBots |
