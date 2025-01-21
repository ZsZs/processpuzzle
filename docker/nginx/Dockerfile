FROM nginx:latest
WORKDIR /usr/share/nginx/html/
ARG CICD_STAGE=docker

COPY dist/apps/processpuzzle-testbed/browser .
COPY docker/nginx/nginx.conf .
COPY docker/nginx/config.$CICD_STAGE.json /usr/share/nginx/html/run-time-conf/config.custom.json

EXPOSE 80
