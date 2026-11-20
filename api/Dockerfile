# Multi-stage production image. Build from the repository root:
# docker build -f api/Dockerfile -t edu360-api:local .
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY api/pom.xml api/pom.xml
COPY api/src api/src
RUN mvn --batch-mode --no-transfer-progress -f api/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system --gid 10001 edu360 && useradd --system --uid 10001 --gid edu360 --home-dir /app edu360
COPY --from=build --chown=edu360:edu360 /workspace/api/target/api-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
