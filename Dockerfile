FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:resolve -q --batch-mode --no-transfer-progress
COPY src src
RUN ./mvnw package -DskipTests -q --batch-mode --no-transfer-progress

FROM eclipse-temurin:25-jre-alpine
RUN apk --no-cache add curl postgresql16-client && \
    addgroup -S app && \
    adduser -S app -G app
USER app
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -sf http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
