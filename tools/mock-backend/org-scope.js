// Maps the org-scoped REST contract (`/organizations/{orgKey}/rules`, see base-rule-api.yaml and
// base-app-api.yaml) onto json-server's flat collections, which may not contain '/'. The collection
// names are the ones db.js generates: `<orgKey>-rules`, `<orgKey>-app-definitions`, `<orgKey>-modules`.
// `<orgKey>-widget-definitions` is the exception — a hand-written fixture in db.json, because
// base-widget-backend ships no YAML loader, so a seed file in its resources would be read by json-server
// alone and would drift from the Java backend unnoticed.
//
// Rewriting the path rather than filtering on an `orgKey` field keeps writes tenant-correct too: a
// POST lands in the collection of the organization named in the URL, and no orgKey ever has to appear
// in a request payload.
//
// Used by the json-server CLI via `--middlewares` and by the Firebase `jsonServer` function, which
// copies this file in its `sync-db` step.
const ORG_SCOPED_PATH = /^\/organizations\/([a-z0-9][a-z0-9-]*)\/(rules|app-definitions|modules|widget-definitions|state-machines)(?=[/?]|$)/;

function orgScopeRewrite(request, response, next) {
  request.url = request.url.replace(ORG_SCOPED_PATH, (_match, orgKey, collection) => `/${orgKey}-${collection}`);
  next();
}

module.exports = orgScopeRewrite;
module.exports.orgScopeRewrite = orgScopeRewrite;
module.exports.ORG_SCOPED_PATH = ORG_SCOPED_PATH;
