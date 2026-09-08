"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.FirebaseFileStorageService = void 0;
const app_1 = require("firebase-admin/app");
const storage_1 = require("firebase-admin/storage");
const node_crypto_1 = require("node:crypto");
const bucket_naming_js_1 = require("./bucket-naming.js");
const object_store_config_js_1 = require("./object-store.config.js");
/**
 * Firebase pendant of `MinioFileStorageService`. Bucket lifecycle operations have no
 * counterpart — the logical bucket is only a key prefix (see `bucket-naming.ts`), so
 * there is nothing to create, drop or probe for existence.
 */
class FirebaseFileStorageService {
    async uploadObject(bucketName, objectName, content, contentType, metadata) {
        await this.file(bucketName, objectName).save(content, {
            contentType,
            resumable: false,
            metadata: {
                contentType,
                // A download token is what makes the emulator fallback in `getObjectUri` work, and
                // it costs nothing in production where signed URLs are used instead.
                metadata: Object.assign(Object.assign({}, metadata), { [FirebaseFileStorageService.DOWNLOAD_TOKEN_KEY]: (0, node_crypto_1.randomUUID)() }),
            },
        });
    }
    /** Undefined — rather than a throw — when the object does not exist, so the caller can answer 404. */
    async getObject(bucketName, objectName) {
        var _a;
        const file = this.file(bucketName, objectName);
        const metadata = await this.metadataOf(file);
        if (!metadata)
            return undefined;
        return {
            stream: file.createReadStream(),
            contentType: typeof metadata.contentType === 'string' ? metadata.contentType : undefined,
            metadata: ((_a = metadata.metadata) !== null && _a !== void 0 ? _a : {}),
        };
    }
    async objectExists(bucketName, objectName) {
        const [exists] = await this.file(bucketName, objectName).exists();
        return exists;
    }
    /**
     * Short-lived read URI. Production signs with the function's service account, which needs
     * `roles/iam.serviceAccountTokenCreator` to call signBlob. The Storage emulator implements
     * no signing at all, so there we hand back the emulator's own tokenized media URL — same
     * contract from the caller's point of view, no expiry.
     */
    async getObjectUri(bucketName, objectName) {
        const file = this.file(bucketName, objectName);
        const emulatorHost = FirebaseFileStorageService.emulatorHost();
        if (emulatorHost)
            return this.emulatorDownloadUri(file, emulatorHost);
        try {
            const [uri] = await file.getSignedUrl({ action: 'read', expires: Date.now() + object_store_config_js_1.SIGNED_URI_TTL_MS });
            return uri;
        }
        catch (error) {
            throw FirebaseFileStorageService.signingFailure(error, file);
        }
    }
    async deleteObject(bucketName, objectName) {
        await this.file(bucketName, objectName).delete({ ignoreNotFound: true });
    }
    file(bucketName, objectName) {
        return FirebaseFileStorageService.bucket().file((0, bucket_naming_js_1.objectPath)(bucketName, objectName));
    }
    async metadataOf(file) {
        try {
            const [metadata] = await file.getMetadata();
            return metadata;
        }
        catch (error) {
            if (FirebaseFileStorageService.isNotFound(error))
                return undefined;
            throw error;
        }
    }
    /**
     * The grant that lets the runtime service account sign lives in the project's IAM policy,
     * not in this repository, so a project that was never given it fails here — reported by the
     * SDK as a bare 403 from an API the caller never knowingly called. Name the missing role
     * instead of leaving that to be rediscovered; anything else is passed through untouched.
     */
    static signingFailure(error, file) {
        const reason = error instanceof Error ? error.message : String(error);
        if (!FirebaseFileStorageService.isSigningDenied(error, reason))
            return error;
        const role = FirebaseFileStorageService.TOKEN_CREATOR_ROLE;
        const message = `Cannot sign a read URI for '${file.name}': the runtime service account needs ${role} on itself to call the IAM signBlob API. ${reason}`;
        return Object.assign(new Error(message), { cause: error });
    }
    /** `getSignedUrl` goes to the network only to sign, so a 403 out of it is a signing denial. */
    static isSigningDenied(error, message) {
        if (message.includes('signBlob'))
            return true;
        if (typeof error !== 'object' || error === null)
            return false;
        const { code, status } = error;
        return code === 403 || status === 403;
    }
    async emulatorDownloadUri(file, emulatorHost) {
        var _a, _b;
        const metadata = await this.metadataOf(file);
        const custom = ((_a = metadata === null || metadata === void 0 ? void 0 : metadata.metadata) !== null && _a !== void 0 ? _a : {});
        const token = (_b = custom[FirebaseFileStorageService.DOWNLOAD_TOKEN_KEY]) === null || _b === void 0 ? void 0 : _b.split(',')[0];
        const path = `${emulatorHost}/v0/b/${file.bucket.name}/o/${encodeURIComponent(file.name)}?alt=media`;
        return token ? `${path}&token=${token}` : path;
    }
    static bucket() {
        if (!(0, app_1.getApps)().length)
            (0, app_1.initializeApp)();
        const bucketName = FirebaseFileStorageService.bucketName();
        return bucketName ? (0, storage_1.getStorage)().bucket(bucketName) : (0, storage_1.getStorage)().bucket();
    }
    /**
     * `getStorage().bucket()` takes its name from `FIREBASE_CONFIG`, which still advertises the
     * legacy `<projectId>.appspot.com` — a bucket that does not exist in projects created after
     * October 2024, so every call 404s with "The specified bucket does not exist". Name the
     * modern default explicitly instead, and keep `OBJECT_STORE_BUCKET` as the override for a
     * project whose bucket follows neither convention.
     */
    static bucketName() {
        const explicit = process.env.OBJECT_STORE_BUCKET;
        if (explicit)
            return explicit;
        const projectId = FirebaseFileStorageService.projectId();
        return projectId ? `${projectId}${FirebaseFileStorageService.DEFAULT_BUCKET_SUFFIX}` : undefined;
    }
    /** Both the functions runtime and the emulator suite set these; `FIREBASE_CONFIG` is the last resort. */
    static projectId() {
        var _a, _b;
        const fromEnv = (_a = process.env.GCLOUD_PROJECT) !== null && _a !== void 0 ? _a : process.env.GOOGLE_CLOUD_PROJECT;
        if (fromEnv)
            return fromEnv;
        try {
            const config = JSON.parse((_b = process.env.FIREBASE_CONFIG) !== null && _b !== void 0 ? _b : '{}');
            return config.projectId;
        }
        catch (_c) {
            return undefined;
        }
    }
    /** `STORAGE_EMULATOR_HOST` is set by the emulator suite, with or without a scheme. */
    static emulatorHost() {
        var _a;
        const host = (_a = process.env.STORAGE_EMULATOR_HOST) !== null && _a !== void 0 ? _a : process.env.FIREBASE_STORAGE_EMULATOR_HOST;
        if (!host)
            return undefined;
        return host.startsWith('http://') || host.startsWith('https://') ? host : `http://${host}`;
    }
    static isNotFound(error) {
        return typeof error === 'object' && error !== null && error.code === 404;
    }
}
exports.FirebaseFileStorageService = FirebaseFileStorageService;
FirebaseFileStorageService.DOWNLOAD_TOKEN_KEY = 'firebaseStorageDownloadTokens';
/** Default Storage bucket of a Firebase project; `.appspot.com` is the pre-October-2024 spelling. */
FirebaseFileStorageService.DEFAULT_BUCKET_SUFFIX = '.firebasestorage.app';
FirebaseFileStorageService.TOKEN_CREATOR_ROLE = 'roles/iam.serviceAccountTokenCreator';
//# sourceMappingURL=firebase-file-storage.service.js.map