# syntax=docker/dockerfile:1.7

# ============================================================================
# STAGE 1: Application builder with optimized caching
# ============================================================================
FROM eclipse-temurin:25-jdk-alpine AS builder

ENV GRADLE_USER_HOME=/cache/.gradle \
    LANG=C.UTF-8 \
    TZ=UTC

# Install build dependencies (minimal)
RUN apk add --no-cache binutils && \
  rm -rf /var/cache/apk/*

WORKDIR /build

# Copy Gradle wrapper and dependency definition files first
# This layer will be cached until these files change
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# Download dependencies with BuildKit cache mount for faster subsequent builds
RUN --mount=type=cache,id=gradle-cache,target=/cache/.gradle,sharing=locked \
  chmod +x gradlew && \
  ./gradlew dependencies --no-daemon --parallel --console=plain

# Copy only production source code (exclude tests, docs, etc.)
COPY src/main/ src/main/

# Build optimized JAR with cache mount
RUN --mount=type=cache,id=gradle-cache,target=/cache/.gradle,sharing=locked \
  ./gradlew bootJar --no-daemon --parallel --console=plain -x test && \
  mkdir -p /app && \
  mv build/libs/*.jar /app/app.jar

WORKDIR /app

# Analyze the dependencies contained in the fat jar
RUN jdeps --ignore-missing-deps -q \
  --recursive \
  --multi-release 25 \
  --print-module-deps \
  --class-path 'BOOT-INF/lib/*' \
  app.jar > deps.info

# Create the custom JRE
RUN jlink \
  --verbose \
  --add-modules "$(cat deps.info),java.base,java.desktop,java.management,java.logging,java.naming,java.instrument,java.sql,java.xml,java.net.http,java.security.sasl,java.security.jgss,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,java.compiler" \
  --compress zip-9 \
  --no-header-files \
  --no-man-pages \
  --output /jre-minimal

# Extract Spring Boot layers for optimal Docker layer caching
RUN java -Djarmode=layertools -jar app.jar extract --destination /app/extracted

# ============================================================================
# STAGE 2: Minimal runtime image
# ============================================================================
FROM alpine:3.21

# Install only critical runtime dependencies
# ca-certificates: for HTTPS connections to AI providers (OpenAI, Anthropic, etc.)
# tini: proper init system for PID 1
# tzdata: timezone support (Vietnam/Ho_Chi_Minh)
# curl: for healthcheck
RUN apk add --no-cache \
  ca-certificates \
  tini \
  tzdata \
  curl && \
  rm -rf /var/cache/apk/* /tmp/*

# Set Vietnam timezone
ENV TZ=Asia/Ho_Chi_Minh

# Create non-root user for security (CIS Docker Benchmark compliance)
RUN addgroup -g 1654 -S appgroup && \
  adduser -u 1654 -S appuser -G appgroup

# Copy minimal custom JRE from builder
COPY --from=builder --chown=1654:1654 /jre-minimal /opt/java

# Set up application directory with proper ownership
WORKDIR /app

# Copy Spring Boot layers in optimal order (least to most frequently changed)
# This maximizes Docker layer cache efficiency
COPY --from=builder --chown=1654:1654 /app/extracted/dependencies/ ./
COPY --from=builder --chown=1654:1654 /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=1654:1654 /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=1654:1654 /app/extracted/application/ ./

# Switch to non-root user (security best practice)
USER 1654:1654

# Set JAVA_HOME and PATH
ENV JAVA_HOME=/opt/java \
    PATH="/opt/java/bin:${PATH}"

# Optimal JVM flags for containerized Spring Boot applications
# - UseContainerSupport: respect container memory limits
# - MaxRAMPercentage: use 75% of container memory for heap
# - UseG1GC: best GC for mixed workloads (AI inference + REST serving)
# - MaxGCPauseMillis: bound pause times for consistent API latency
# - UseStringDeduplication: reduce heap pressure from repeated legal text strings
# - ExitOnOutOfMemoryError: fail fast rather than degraded state
# - java.security.egd: faster SecureRandom for Spring Security token generation
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+UseStringDeduplication \
  -XX:+ParallelRefProcEnabled \
  -XX:+DisableExplicitGC \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Djava.awt.headless=true"

# Spring Boot default port
EXPOSE 8089

# OCI labels for traceability and compliance
LABEL org.opencontainers.image.title="Vietnam Traffic Law Chatbot" \
  org.opencontainers.image.description="AI-powered legal Q&A chatbot for Vietnamese traffic law — source-grounded answers backed by vector search over trusted legal sources" \
  org.opencontainers.image.vendor="kl3inIT" \
  org.opencontainers.image.authors="kl3inIT" \
  org.opencontainers.image.source="https://github.com/kl3inIT/ai-traffic-law-chatbot" \
  org.opencontainers.image.version="1.0.0" \
  org.opencontainers.image.revision="main" \
  org.opencontainers.image.licenses="MIT" \
  org.opencontainers.image.base.name="docker.io/library/alpine:3.21" \
  maintainer="kl3inIT"

# Health check using Spring Boot Actuator /health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8089/actuator/health || exit 1

# Use tini as init system for proper signal handling
# Ensures graceful shutdown and zombie process reaping
ENTRYPOINT ["/sbin/tini", "--"]

# Run Spring Boot application via layered launcher
CMD ["java", "org.springframework.boot.loader.launch.JarLauncher"]
