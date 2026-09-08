"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.PayloadTooLargeError = void 0;
exports.parseMultipart = parseMultipart;
const busboy_1 = __importDefault(require("busboy"));
const object_store_config_js_1 = require("./object-store.config.js");
class PayloadTooLargeError extends Error {
    constructor() {
        super(`Uploaded file exceeds the ${object_store_config_js_1.MAX_UPLOAD_BYTES} byte limit.`);
        this.name = 'PayloadTooLargeError';
    }
}
exports.PayloadTooLargeError = PayloadTooLargeError;
/**
 * Cloud Functions reads the whole request into `rawBody` before the handler runs, so the
 * request stream is already consumed and `multer` and friends never see a chunk. Feeding
 * `rawBody` into busboy is the supported way to read multipart in a function; the `pipe`
 * branch keeps the same code working when the express app is served directly (tests, a
 * local `express().listen()`).
 */
function parseMultipart(request) {
    return new Promise((resolve, reject) => {
        const busboy = (0, busboy_1.default)({ headers: request.headers, limits: { fileSize: object_store_config_js_1.MAX_UPLOAD_BYTES, files: 1 } });
        const fields = {};
        const files = [];
        let settled = false;
        let truncated = false;
        const fail = (error) => {
            if (settled)
                return;
            settled = true;
            reject(error);
        };
        const succeed = () => {
            if (settled)
                return;
            settled = true;
            if (truncated)
                reject(new PayloadTooLargeError());
            else
                resolve({ fields, files });
        };
        busboy.on('field', (name, value) => {
            fields[name] = value;
        });
        busboy.on('file', (fieldName, stream, info) => {
            const chunks = [];
            stream.on('data', (chunk) => chunks.push(chunk));
            stream.on('limit', () => {
                truncated = true;
            });
            stream.on('error', fail);
            stream.on('end', () => {
                files.push({ fieldName, fileName: info.filename, mimeType: info.mimeType, content: Buffer.concat(chunks) });
            });
        });
        busboy.on('error', (error) => fail(error instanceof Error ? error : new Error(String(error))));
        // busboy 1.x signals completion with `close`; `finish` is the 0.x name. Listening to
        // both keeps the parser working across either resolution, the guard makes it idempotent.
        busboy.on('close', succeed);
        busboy.on('finish', succeed);
        const rawBody = request.rawBody;
        if (rawBody)
            busboy.end(rawBody);
        else
            request.pipe(busboy);
    });
}
//# sourceMappingURL=multipart.js.map