# The jar is built by Maven beforehand (CI or `mvn package`): kubsei-*-lib come from GitHub Packages
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache dumb-init curl &&     addgroup -S -g 1001 spring && adduser -S -u 1001 -G spring spring

WORKDIR /app
COPY target/*.jar app.jar
USER spring

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"     SERVER_PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3     CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
