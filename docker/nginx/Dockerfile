FROM nginx:latest
WORKDIR /usr/share/nginx/html/gui/processpuzzle-testbed/

COPY ./dist/apps/processpuzzle-testbed/browser .
COPY ./docker/nginx/nginx.conf /etc/nginx/nginx.conf
COPY ./docker/nginx/config.docker.json /usr/share/nginx/html/gui/processpuzzle-testbed/run-time-conf/
