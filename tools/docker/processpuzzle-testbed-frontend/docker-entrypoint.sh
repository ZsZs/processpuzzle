#!/bin/sh
set -e

: "${PIPELINE_STAGE:=ci}"

mkdir -p /usr/share/nginx/html/assets

envsubst '${PIPELINE_STAGE}' \
  < /etc/templates/runtime-env.json.template \
  > /usr/share/nginx/html/assets/runtime-env.json

exec nginx -g 'daemon off;'
