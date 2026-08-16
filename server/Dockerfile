FROM gradle:8.5-jdk17 AS builder

WORKDIR /app
COPY settings.gradle.kts .
COPY src ./src

RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S twyn && adduser -S twyn -G twyn

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

RUN mkdir -p /tmp/twyn-media && chown -R twyn:twyn /tmp/twyn-media
USER twyn

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
