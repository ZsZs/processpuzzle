#!/bin/sh
set -e

# The one variable this image is parameterised by: it selects which `config.<stage>.json` the
# application merges over `config.common.json`, which is what lets a single image serve every stage.
# Unlike the testbed's entrypoint there is no `FIREBASE_API_KEY` — platform-admin talks to nothing but
# its own backend and Keycloak.
: "${PIPELINE_STAGE:=ci}"

mkdir -p /usr/share/nginx/html/assets

envsubst '${PIPELINE_STAGE}' \
  < /etc/templates/runtime-env.json.template \
  > /usr/share/nginx/html/assets/runtime-env.json

exec nginx -g 'daemon off;'
