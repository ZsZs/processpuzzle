FROM nginx:latest
WORKDIR /usr/share/nginx/html/

COPY dist/apps/processpuzzle-testbed/browser .
COPY docker/nginx/nginx.conf .
COPY docker/nginx/config.docker.json /usr/share/nginx/html/run-time-conf
EXPOSE 80
