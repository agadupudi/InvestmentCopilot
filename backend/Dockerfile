# syntax=docker/dockerfile:1.7

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Cache Gradle wrapper + config first to keep dep resolution layer hot
COPY gradle gradle
COPY gradlew settings.gradle build.gradle ./
COPY backend/build.gradle backend/build.gradle

# Prime the wrapper and dependency cache
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :backend:help

# Now copy the actual source and build the fat jar
COPY backend/src backend/src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :backend:bootJar

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /workspace/backend/build/libs/*.jar /app/app.jar

EXPOSE 8000

# Container-level health check (override with nothing for ECS — ALB does the real check)
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8000/health > /dev/null || exit 1

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
