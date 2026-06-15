FROM nginx:latest
WORKDIR /usr/share/nginx/html/
ARG CICD_STAGE=ci

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY dist/apps/processpuzzle-testbed/browser .
COPY tools/docker/testbed/config.${CICD_STAGE}.json /usr/share/nginx/html/run-time-conf
COPY tools/docker/testbed/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
