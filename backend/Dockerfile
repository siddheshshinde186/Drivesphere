# Stage 1: Build application JAR using Eclipse Temurin 21 and Maven
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

# Copy Maven wrapper and POM first for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copy source code and build production package
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal, secure JRE runtime
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Create non-root system user for security
RUN addgroup --system drivesphere && adduser --system --ingroup drivesphere drivesphere

# Install curl for reliable container health check probes
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Create uploads directory with appropriate permissions
RUN mkdir -p /app/uploads/documents && chown -R drivesphere:drivesphere /app

# Copy executable jar from builder stage
COPY --from=builder --chown=drivesphere:drivesphere /build/target/*.jar app.jar

USER drivesphere

# Default container-aware memory allocation for 512MB RAM targets
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:InitialRAMPercentage=40.0 -XX:+UseG1GC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/api/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
