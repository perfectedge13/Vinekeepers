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
# Ubuntu Jammy runtime: apt-installed Ansible avoids Alpine/musl pip failures (cryptography/rust wheels).
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# ensure_repo_workspace: git + ssh for clones; Ansible for Gadget deploy; Docker CLI + compose plugin
# for Gadget host compose operations via /var/run/docker.sock; mount volumes to persist checkouts.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates curl gnupg git openssh-client ansible \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
    && chmod a+r /etc/apt/keyrings/docker.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu jammy stable" > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        docker-ce-cli docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/* \
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
