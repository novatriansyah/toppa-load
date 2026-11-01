# ==============================================================================
# Stage 1: Build Frontend React SPA
# ==============================================================================
FROM node:24-alpine AS web-build
WORKDIR /app/web

# Install dependencies first for Docker layer caching
COPY web/package.json web/package-lock.json ./
RUN npm ci

# Copy frontend source code and build production bundle into web/dist
COPY web/ ./
RUN npm run build

# ==============================================================================
# Stage 2: Build Spring Boot Application with static SPA embedded
# ==============================================================================
FROM eclipse-temurin:25-jdk AS server-build
WORKDIR /app/server

# Copy Maven wrapper and POM for dependency resolution caching
COPY server/pom.xml server/mvnw ./
COPY server/.mvn .mvn
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy server source code
COPY server/src ./src

# Copy built SPA artifacts into Spring Boot static resources directory
COPY --from=web-build /app/web/dist /app/server/src/main/resources/static/

# Package executable JAR skipping tests (tests validated in CI test jobs)
RUN ./mvnw package -DskipTests -B

# ==============================================================================
# Stage 3: Minimal Secure Production Runtime
# ==============================================================================
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Install wget for healthcheck and create non-root user appuser (UID 1001)
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -g 1001 appuser && \
    useradd -u 1001 -g 1001 -s /bin/false -M appuser

# Copy executable JAR from server-build stage
COPY --from=server-build --chown=appuser:appuser /app/server/target/*.jar /app/app.jar

# JVM resource limits (50% max RAM, 512MB max heap) and UTC timezone
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Xmx512m -Duser.timezone=UTC"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

USER appuser

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
