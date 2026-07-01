FROM eclipse-temurin:21-jre-alpine
RUN apk --no-cache add curl postgresql16-client && \
    addgroup -S app && \
    adduser -S app -G app
USER app
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -sf http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
