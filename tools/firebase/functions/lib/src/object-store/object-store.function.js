"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.objectStore = void 0;
exports.createObjectStoreRouter = createObjectStoreRouter;
exports.createObjectStoreApp = createObjectStoreApp;
const express_1 = __importStar(require("express"));
const cors_1 = __importDefault(require("cors"));
const firebase_functions_1 = require("firebase-functions");
const https_1 = require("firebase-functions/https");
const object_store_config_js_1 = require("./object-store.config.js");
const object_store_handlers_js_1 = require("./object-store.handlers.js");
/**
 * `Location` carries the bucket name of a freshly uploaded object and `X-Object-*` the
 * object's identity — a browser cannot read either from a cross-origin response unless
 * they are explicitly exposed, and `ObjectStoreService` reads all three.
 */
const EXPOSED_HEADERS = ['Location', 'X-Object-Name', 'X-Object-Bucket'];
function createObjectStoreRouter(handlers = new object_store_handlers_js_1.ObjectStoreHandlers()) {
    const router = (0, express_1.Router)();
    router.post('/objects', handlers.uploadObject);
    router.get('/objects/:bucketName/:objectID', handlers.getObjectByID);
    router.delete('/objects/:bucketName/:objectID', handlers.deleteObjectByID);
    router.get('/objects/:bucketName/:objectID/uri', handlers.getObjectUriByID);
    router.get('/objects/:bucketName/:objectID/thumbnail-uri', handlers.getThumbnailUriByID);
    return router;
}
function createObjectStoreApp(handlers) {
    const app = (0, express_1.default)();
    app.use((0, cors_1.default)({ origin: true, exposedHeaders: EXPOSED_HEADERS }));
    const router = createObjectStoreRouter(handlers);
    // Behind the Hosting rewrite the function sees the full `/api/store/...` path; called
    // directly (emulator function URL, tests) it sees the bare path. Both are served.
    app.use(object_store_config_js_1.API_BASE_PATH, router);
    app.use('/', router);
    app.use((error, _request, response, next) => {
        firebase_functions_1.logger.error('Object store request failed', error);
        if (response.headersSent) {
            next(error);
            return;
        }
        response.status(500).json({ errorId: 'store.internal-error', errorText: error.message });
    });
    return app;
}
/**
 * `invoker` is stated rather than left to default because the default only applies when the function is
 * *created*: on an update `firebase deploy` re-applies the Cloud Run IAM policy only for an explicitly declared
 * invoker, so a service that never got the `allUsers` grant stays private through every later deploy — and an
 * anonymous browser upload is then rejected by IAP ("Invalid IAP credentials: empty token") before the handler
 * runs. Declaring it makes each deploy assert the grant.
 */
exports.objectStore = (0, https_1.onRequest)({ region: 'europe-central2', memory: '512MiB', timeoutSeconds: 120, invoker: 'public' }, createObjectStoreApp());
//# sourceMappingURL=object-store.function.js.map