# Vinekeepers — AI-based bots. Java 21, Maven build.
# Build: docker build -t vinekeepers .
# Run:   docker run --rm -e DISCORD_TOKEN=... vinekeepers
# Mount config: docker run -v $(pwd)/config:/app/config -v $(pwd)/.env:/app/.env vinekeepers

# ---- Build ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN apk add --no-cache maven

COPY src ./src
RUN mvn -B package -Dmaven.test.skip=true && \
    mvn -B dependency:copy-dependencies -DoutputDirectory=target/lib

# ---- Run ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Config and optional .env (can override by mount)
COPY --from=builder /build/target/vinekeepers-*.jar ./app.jar
COPY --from=builder /build/target/lib ./lib
COPY config ./config

# Default: run the app (Discord/GitHub tokens via env or .env)
ENV JAVA_OPTS=""
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
