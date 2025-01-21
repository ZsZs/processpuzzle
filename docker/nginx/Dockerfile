FROM nginx:latest
WORKDIR /usr/share/nginx/html/
ARG cicd_stage=docker

COPY dist/apps/processpuzzle-testbed/browser .
COPY docker/nginx/nginx.conf .
COPY docker/nginx/config.$cicd_stage.json /usr/share/nginx/html/run-time-conf/config.custom.json

EXPOSE 80
