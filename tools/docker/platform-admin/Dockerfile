FROM nginx:latest
WORKDIR /usr/share/nginx/html/

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gettext-base \
    && rm -rf /var/lib/apt/lists/*

COPY dist/apps/platform-admin/browser .
COPY tools/docker/platform-admin/runtime-env.json.template /etc/templates/runtime-env.json.template
COPY tools/docker/platform-admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY tools/docker/platform-admin/docker-entrypoint.sh /docker-entrypoint.d/40-render-runtime-env.sh
# The repository is checked out on Windows, so the script arrives with CRLF line endings that `sh`
# reports as `not found` on the shebang line.
RUN sed -i 's/\r$//' /docker-entrypoint.d/40-render-runtime-env.sh \
    && chmod +x /docker-entrypoint.d/40-render-runtime-env.sh

EXPOSE 80
