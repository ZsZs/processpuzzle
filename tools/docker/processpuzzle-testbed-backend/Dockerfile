# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jre

LABEL authors="Zsolt Zsuffa"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY dist/apps/processpuzzle-testbed-backend/processpuzzle-testbed-backend.jar ./processpuzzle-testbed-backend.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/processpuzzle-testbed-backend.jar"]
