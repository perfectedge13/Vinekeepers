# AGENTS.md

## Cursor Cloud specific instructions

### Services overview

Vinekeepers is a pure Java 21 / Maven project with no external service dependencies. All connectors (Discord, GitHub, Cursor Cloud) are stubs. There is no database, message queue, or Docker setup.

### Build and run

Standard commands are in the README. Key commands:

- **Compile:** `mvn compile`
- **Test:** `mvn test` (97 JUnit 5 tests)
- **Run:** `mvn -q exec:java -Dexec.mainClass="com.vinekeepers.VinekeepersApp"`
- **Spec validation:** `npm run validate-specs` and `npm run validate-drift`

### Non-obvious caveats

- **Maven version matters:** The Ubuntu apt-packaged Maven 3.8.7 ships with Surefire 2.12.4, which cannot discover JUnit 5 (Jupiter) tests — `mvn test` silently runs 0 tests. Maven 3.9.9 (installed to `/opt/maven`) ships with Surefire 3.x and correctly runs all 97 tests. Always use `/usr/local/bin/mvn` (symlinked to `/opt/maven/bin/mvn`).
- **No Maven wrapper:** The repo has no `mvnw`; a system-installed Maven is required.
- **Node.js dependencies** (in root `package.json`) are only for spec validation scripts (`ajv`, `js-yaml`), not for the Java application itself.
- **The app exits immediately** in stub mode after processing one round of stub events. This is expected behavior — the stubs fire a single event then stop.
- **`.env` file** is optional; the app loads it if present but works without it. Relevant env vars: `CURSOR_API_KEY`, `CURSOR_API_BASE_URL`.
