"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.jsonServer = exports.helloWorld = exports.baseDocument = exports.objectStore = void 0;
const https_1 = require("firebase-functions/https");
const firebase_functions_1 = require("firebase-functions");
const json_server_1 = require("json-server");
const db_json_1 = __importDefault(require("./db.json"));
var object_store_function_js_1 = require("./src/object-store/object-store.function.js");
Object.defineProperty(exports, "objectStore", { enumerable: true, get: function () { return object_store_function_js_1.objectStore; } });
// Serves base-document-api.yaml from Firestore. It must be matched ahead of `jsonServer` in the
// `firebase.json` rewrites, which still owns the rest of `/api/**` — the third-party REST fixtures in
// `db.json`.
var base_document_function_js_1 = require("./src/base-document/base-document.function.js");
Object.defineProperty(exports, "baseDocument", { enumerable: true, get: function () { return base_document_function_js_1.baseDocument; } });
(0, firebase_functions_1.setGlobalOptions)({ region: 'europe-central2' });
exports.helloWorld = (0, https_1.onRequest)({ region: 'europe-central2' }, (request, response) => {
    firebase_functions_1.logger.info('Hello logs!', { structuredData: true });
    response.send('Hello from Firebase!');
});
firebase_functions_1.logger.info(`Database: ${db_json_1.default}`);
const api = (0, json_server_1.create)();
const server = (0, json_server_1.create)();
const middlewares = (0, json_server_1.defaults)();
const jsonServerRouter = (0, json_server_1.router)('db.json');
api.use(middlewares);
// `db.json` is the third-party-source mock (see tools/mock-backend/README.md), so nothing here is
// org-scoped: the platform's own features are served by their backends, not by json-server.
api.use(jsonServerRouter);
server.use('/api', api);
//const port = 3000;
// server.listen(port, () => {
//   logger.info(`JSON Server is running on port ${port}`);
// });
exports.jsonServer = (0, https_1.onRequest)(server);
//# sourceMappingURL=index.js.map