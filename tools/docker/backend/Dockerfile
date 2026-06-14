# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jre

LABEL authors="zsolt"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY dist/apps/processpuzzle-backend/processpuzzle-backend.jar ./processpuzzle-backend.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/processpuzzle-backend.jar"]
