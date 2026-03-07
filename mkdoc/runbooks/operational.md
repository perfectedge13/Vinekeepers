# Operational

# Procedures

## Build and test

1. Compile: `mvn compile`
2. Run tests: `mvn test`
3. Package: `mvn package` (optional)

## Run the application

1. Ensure Java 21 and Maven are installed.
2. Optionally place a `.env` file in the project root (for wiki or other credentials).
3. Run the main class `com.vinekeepers.VinekeepersApp` from the IDE or after packaging (e.g. run the JAR with dependencies on the classpath).

## Validate specs

When `npm run validate-specs` or equivalent is configured: run it to ensure `specs/specs.yml` and registry files conform to their schemas.
