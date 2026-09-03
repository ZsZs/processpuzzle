FROM nginx:latest
WORKDIR /usr/share/nginx/html/

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gettext-base \
    && rm -rf /var/lib/apt/lists/*

COPY dist/apps/processpuzzle-testbed-frontend/browser .
COPY tools/docker/processpuzzle-testbed-frontend/runtime-env.json.template /etc/templates/runtime-env.json.template
COPY tools/docker/processpuzzle-testbed-frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY tools/docker/processpuzzle-testbed-frontend/docker-entrypoint.sh /docker-entrypoint.d/40-render-runtime-env.sh
RUN sed -i 's/\r$//' /docker-entrypoint.d/40-render-runtime-env.sh \
    && chmod +x /docker-entrypoint.d/40-render-runtime-env.sh

EXPOSE 80
