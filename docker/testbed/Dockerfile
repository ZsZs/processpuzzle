FROM nginx:latest
WORKDIR /usr/share/nginx/html/
ARG CICD_STAGE=dev

COPY dist/apps/processpuzzle-testbed .
COPY docker/testbed/config.${CICD_STAGE}.json /usr/share/nginx/html/run-time-conf/config.custom.json
COPY docker/testbed/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
