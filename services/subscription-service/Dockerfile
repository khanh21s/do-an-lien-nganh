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
RUN addgroup -S -g 1001 appuser && adduser -S -u 1001 -G appuser appuser
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser
EXPOSE 8005
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8005/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
