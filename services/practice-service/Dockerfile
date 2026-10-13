# Build stage (Java 21 - must match <java.version> in pom.xml)
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
# Fixed uid/gid so content-service and practice-service share the media_data volume
RUN addgroup -S -g 1001 appuser && adduser -S -u 1001 -G appuser appuser
# Local storage root for mock mode (MEDIA_ROOT, docker volume media_data)
RUN mkdir -p /data/media && chown -R appuser:appuser /data/media
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser
EXPOSE 8003
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8003/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
