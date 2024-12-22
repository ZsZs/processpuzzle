FROM nginx:latest
WORKDIR /usr/share/nginx/html/gui/qk-ng/

COPY ./dist/ef-qk-ng/browser .
COPY ./docker/nginx/nginx.conf /etc/nginx/nginx.conf
COPY ./docker/nginx/config.docker.json /usr/share/nginx/html/gui/qk-ng/run-time-conf/
