#!/bin/sh
set -e

: "${PIPELINE_STAGE:=ci}"
: "${FIREBASE_API_KEY:?FIREBASE_API_KEY must be set}"

mkdir -p /usr/share/nginx/html/assets

envsubst '${PIPELINE_STAGE} ${FIREBASE_API_KEY}' \
  < /etc/templates/runtime-env.json.template \
  > /usr/share/nginx/html/assets/runtime-env.json

exec nginx -g 'daemon off;'
