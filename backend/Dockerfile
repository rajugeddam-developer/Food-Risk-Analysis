# ==============================================================================
# Food Risk Analysis - Backend Production Dockerfile
# Multi-stage build providing native Tesseract C++ libraries and Temurin JRE
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build JAR with Maven & OpenJDK 21
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-jammy AS builder
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy sources and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal Production Runtime with Tesseract OCR Native Libraries
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install native Tesseract OCR C++ libraries required by Tess4J (JNA)
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    libtesseract-dev \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create unprivileged application user
RUN groupadd -r appgroup && useradd -r -g appgroup -s /bin/false appuser

# Copy packaged JAR from builder
COPY --from=builder /workspace/target/*.jar /app/app.jar

# Copy local traineddata into app directory
COPY tessdata /app/tessdata

# Configure environment variables
ENV TESSDATA_PATH=/app/tessdata \
    TESSERACT_LANGUAGE=eng \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

# Set directory permissions for unprivileged user
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# Container liveness health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:8080/api/health/live || curl -f http://localhost:8080/api/health || exit 1

# Optimized containerized JVM flags
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-Xmx320m", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
