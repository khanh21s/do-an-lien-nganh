# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl
RUN addgroup -S appuser && adduser -S appuser -G appuser
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appuser /app/app.jar
USER appuser
EXPOSE 8003
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8003/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
