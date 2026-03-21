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

# ensure_repo_workspace: git on PATH; openssh for git@ clones; stable clone root (mount a volume here to persist)
# Deploy workflow: ansible-playbook + git (branch list); mount host paths and /var/run/docker.sock as needed.
RUN apk add --no-cache git openssh-client python3 py3-pip \
    && pip3 install --break-system-packages ansible-core \
    && mkdir -p /app/checkouts
ENV VINEKEEPERS_REPO_WORKSPACE_ROOT=/app/checkouts
ENV GADGET_ANSIBLE_ROOT=/app/ansible

# Config and optional .env (can override by mount)
COPY --from=builder /build/target/vinekeepers-*.jar ./app.jar
COPY --from=builder /build/target/lib ./lib
COPY config ./config
COPY ansible ./ansible

# Default: run the app (Discord/GitHub tokens via env or .env)
ENV JAVA_OPTS=""
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
