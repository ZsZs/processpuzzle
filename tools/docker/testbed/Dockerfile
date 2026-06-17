FROM nginx:latest
WORKDIR /usr/share/nginx/html/

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gettext-base \
    && rm -rf /var/lib/apt/lists/*

COPY dist/apps/processpuzzle-testbed/browser .
COPY tools/docker/testbed/runtime-env.json.template /etc/templates/runtime-env.json.template
COPY tools/docker/testbed/nginx.conf /etc/nginx/conf.d/default.conf
COPY tools/docker/testbed/docker-entrypoint.sh /docker-entrypoint.d/40-render-runtime-env.sh
RUN chmod +x /docker-entrypoint.d/40-render-runtime-env.sh

EXPOSE 80
