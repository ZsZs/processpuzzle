# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jre

LABEL authors="zsolt"

WORKDIR /app

COPY apps/processpuzzle-backend/target/processpuzzle-backend-*.jar ./processpuzzle-backend.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/processpuzzle-backend.jar"]
