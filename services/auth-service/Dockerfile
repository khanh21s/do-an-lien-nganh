# Build stage (Java 21 - must match <java.version> in pom.xml).
# Build context is ./services (docker-compose.yml: context ./services, dockerfile auth-service/Dockerfile)
# so the shared module services/common (com.parroto:parroto-common) can be built first.
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY common/pom.xml common/pom.xml
RUN mvn -B -f common/pom.xml dependency:go-offline
COPY common/src common/src
RUN mvn -B -f common/pom.xml install -DskipTests
COPY auth-service/pom.xml auth-service/pom.xml
RUN mvn -B -f auth-service/pom.xml dependency:go-offline
COPY auth-service/src auth-service/src
RUN mvn -B -f auth-service/pom.xml clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
RUN addgroup -S -g 1001 appuser && adduser -S -u 1001 -G appuser appuser
WORKDIR /app
COPY --from=builder /app/auth-service/target/*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser
EXPOSE 8001
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8001/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
