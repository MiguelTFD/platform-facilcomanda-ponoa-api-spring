# ==============================================================================
# STAGE 1: Builder
# ==============================================================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

# Copy Maven wrapper and descriptor to leverage Docker cache for dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Ensure wrapper script is executable and resolve dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copy source code and build production jar (excluding tests for fast build)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# STAGE 2: Hardened Production Runtime
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runner

# Install curl for healthchecks and tzdata for local timezone (Peru / SUNAT)
RUN apk add --no-cache curl tzdata \
    && cp /usr/share/zoneinfo/America/Lima /etc/localtime \
    && echo "America/Lima" > /etc/timezone

# Security: Create non-root user and group
RUN addgroup -g 10001 -S spring && adduser -u 10001 -S spring -G spring

WORKDIR /app

# JVM flags tuned for containers: dynamic RAM sizing, OOM protection, entropy
ENV TZ=America/Lima \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Copy compiled jar from builder stage
COPY --from=builder --chown=spring:spring /workspace/target/*.jar app.jar

# Drop privileges to non-root user
USER spring:spring

EXPOSE 8081

# Container healthcheck using Spring Boot Actuator liveness probe
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
