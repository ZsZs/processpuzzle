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
Object.defineProperty(exports, "__esModule", { value: true });
exports.SampleDocumentError = exports.SAMPLE_DOCUMENTS_DIR = exports.DOCUMENTS_FILE_SUFFIX = void 0;
exports.findRepositoryRoot = findRepositoryRoot;
exports.resolveSampleDocumentsDir = resolveSampleDocumentsDir;
exports.listSampleDocumentFiles = listSampleDocumentFiles;
exports.readSampleDocumentFile = readSampleDocumentFile;
exports.parseSampleDocuments = parseSampleDocuments;
exports.seedSampleDocuments = seedSampleDocuments;
const node_fs_1 = require("node:fs");
const node_path_1 = require("node:path");
const yaml_1 = require("yaml");
const base_document_config_js_1 = require("./base-document.config.js");
const mapper = __importStar(require("./document-mapper.js"));
/**
 * Firebase-side counterpart of `SampleDocumentLoader`: imports the bundled sample documents so a
 * deployed environment has something to read and edit instead of an empty Documents list.
 *
 * The two topologies seed from **the same files** — `base-document-backend`'s
 * `sample-documents/<orgKey>-documents.yaml` — for the reason the platform runs both in CI at all: a
 * sample that exists on only one of them makes the Documents page a different feature depending on
 * where it is deployed. Adding a tenant is adding a file here too, exactly as the Java loader
 * documents.
 *
 * <h2>Why this is a deploy-time script rather than a function</h2>
 *
 * The Java loader runs on `ApplicationReadyEvent`, which a Cloud Function has no equivalent of: it is
 * started per request, by any of an unbounded number of instances, so "on startup" would mean "on
 * every cold start, concurrently". Seeding is therefore an explicit step of the deploy workflow,
 * which also gives it something the loader cannot have — see {@link SeedOptions.reset}.
 *
 * <h2>What is deliberately not seeded</h2>
 *
 * Publication. The Java loader publishes every locale of a public sample, because there the public
 * read path serves snapshots. In this topology publish, unpublish and `getPublishedContent` are the
 * operations `BaseDocumentHandlers` defers, so a `published` snapshot written here would be read by
 * nothing, while `deriveStatus` would start reporting `PUBLISHED` for content no endpoint can serve.
 * Samples are seeded as drafts, and `publishedAt` stays null until publishing exists.
 */
/** Same convention as `<orgKey>-rules.yaml` and `<orgKey>-apps.yaml`: the owning tenant is the file name. */
exports.DOCUMENTS_FILE_SUFFIX = '-documents.yaml';
/** Where both platforms' samples live, relative to the repository root. */
exports.SAMPLE_DOCUMENTS_DIR = (0, node_path_1.join)('libs', 'java-shared', 'base-document-backend', 'src', 'main', 'resources', 'sample-documents');
class SampleDocumentError extends Error {
}
exports.SampleDocumentError = SampleDocumentError;
/**
 * Walks up from `from` to the directory holding `nx.json`.
 *
 * The samples are addressed from the repository root rather than relative to this module, because
 * this module is loaded from `lib/` after compilation and from `src/` by its spec, and a
 * `../../..`-style path would have to be wrong in one of the two.
 */
function findRepositoryRoot(from = process.cwd()) {
    let current = (0, node_path_1.resolve)(from);
    for (;;) {
        if ((0, node_fs_1.existsSync)((0, node_path_1.join)(current, 'nx.json')))
            return current;
        const parent = (0, node_path_1.dirname)(current);
        if (parent === current)
            throw new SampleDocumentError(`No nx.json in '${(0, node_path_1.resolve)(from)}' or any parent; cannot locate ${exports.SAMPLE_DOCUMENTS_DIR}.`);
        current = parent;
    }
}
function resolveSampleDocumentsDir(from) {
    return (0, node_path_1.join)(findRepositoryRoot(from), exports.SAMPLE_DOCUMENTS_DIR);
}
/** The sample files of `directory`, in name order so a run's log is comparable to the previous one's. */
function listSampleDocumentFiles(directory) {
    if (!(0, node_fs_1.existsSync)(directory))
        throw new SampleDocumentError(`Sample documents directory '${directory}' does not exist.`);
    return (0, node_fs_1.readdirSync)(directory)
        .filter((name) => name.endsWith(exports.DOCUMENTS_FILE_SUFFIX))
        .sort();
}
function readSampleDocumentFile(directory, fileName) {
    return parseSampleDocuments(fileName, (0, node_fs_1.readFileSync)((0, node_path_1.join)(directory, fileName), 'utf-8'));
}
/**
 * Validates only what would otherwise be stored as a broken document: the three required properties
 * and the shape around them. Everything else is left to the same defaulting the API applies, since
 * `toStoredProperties` is what writes the row either way.
 *
 * Malformed input throws rather than being skipped with a warning: a sample file is ours, so a typo
 * in it is a build mistake, and a deploy that silently seeded one of two documents is worse than one
 * that stops and says which entry is wrong.
 */
function parseSampleDocuments(fileName, yamlText) {
    if (!fileName.endsWith(exports.DOCUMENTS_FILE_SUFFIX))
        throw new SampleDocumentError(`'${fileName}' is not a sample document file; the name must end with '${exports.DOCUMENTS_FILE_SUFFIX}'.`);
    const orgKey = fileName.slice(0, -exports.DOCUMENTS_FILE_SUFFIX.length);
    if (!orgKey)
        throw new SampleDocumentError(`'${fileName}' names no organization; the name must be '<orgKey>${exports.DOCUMENTS_FILE_SUFFIX}'.`);
    const parsed = (0, yaml_1.parse)(yamlText);
    const documents = parsed === null || parsed === void 0 ? void 0 : parsed.documents;
    if (!Array.isArray(documents))
        throw new SampleDocumentError(`'${fileName}' has no 'documents' list.`);
    documents.forEach((document, index) => validate(document, `${fileName} documents[${index}]`));
    return { orgKey, fileName, documents: documents };
}
/**
 * Imports `file`'s documents into `orgKey`, creating each one exactly as `createDocument` and
 * `addDocumentTranslation` would: the source-locale draft first, then the other locales branched from
 * its revision, with the declared block ids preserved.
 *
 * Preserving them is not cosmetic. A `widgetEmbed` node names its widget block by id, and the sample
 * deliberately reuses the same widget ids across locales; minting fresh ones would leave every
 * embed pointing at nothing.
 */
async function seedSampleDocuments(store, file, options = {}) {
    const deleted = options.reset ? await deleteAll(store, file.orgKey) : 0;
    const imported = [];
    const skipped = [];
    for (const input of file.documents) {
        if (!options.reset && (await store.findDocumentBySlug(file.orgKey, input.slug))) {
            skipped.push(input.slug);
            continue;
        }
        await importDocument(store, file.orgKey, input);
        imported.push(input.slug);
    }
    return { orgKey: file.orgKey, imported, skipped, deleted };
}
async function deleteAll(store, orgKey) {
    const documents = await store.listDocuments(orgKey, base_document_config_js_1.MAX_LIST_SCAN);
    for (const document of documents)
        await store.deleteDocument(orgKey, document.id);
    return documents.length;
}
async function importDocument(store, orgKey, input) {
    var _a, _b;
    const document = mapper.toNewStoredDocument(orgKey, input, null);
    await store.saveDocument(document);
    const translations = (_a = input.translations) !== null && _a !== void 0 ? _a : [];
    const source = translations.find((translation) => translation.locale === document.sourceLocale);
    const sourceBlocks = mapper.toBlocks((_b = source === null || source === void 0 ? void 0 : source.blocks) !== null && _b !== void 0 ? _b : []);
    const sourceDraft = mapper.newDraft(document.sourceLocale, sourceBlocks, null, document.createdAt);
    await store.saveDraft(orgKey, document.id, sourceDraft);
    for (const translation of translations.filter((candidate) => candidate.locale !== document.sourceLocale)) {
        // `blocks` absent copies the source locale, the same distinction `addDocumentTranslation` draws
        // between an absent list and an explicit empty one.
        const blocks = translation.blocks == null ? sourceBlocks : mapper.toBlocks(translation.blocks);
        await store.saveDraft(orgKey, document.id, mapper.newDraft(translation.locale, blocks, sourceDraft.revision, document.createdAt));
    }
    return document;
}
function validate(document, where) {
    var _a;
    if (typeof document !== 'object' || document === null || Array.isArray(document))
        throw new SampleDocumentError(`${where} is not a document.`);
    const candidate = document;
    for (const field of ['slug', 'title', 'sourceLocale']) {
        if (typeof candidate[field] !== 'string' || ((_a = candidate[field]) === null || _a === void 0 ? void 0 : _a.trim()) === '')
            throw new SampleDocumentError(`${where} has no '${field}'.`);
    }
    const translations = candidate.translations;
    if (translations === undefined || translations === null)
        return;
    if (!Array.isArray(translations))
        throw new SampleDocumentError(`${where} has a 'translations' that is not a list.`);
    translations.forEach((translation, index) => {
        const locale = translation === null || translation === void 0 ? void 0 : translation.locale;
        if (typeof locale !== 'string' || locale.trim() === '')
            throw new SampleDocumentError(`${where} translations[${index}] has no 'locale'.`);
    });
    const locales = translations.map((translation) => translation.locale);
    const duplicate = locales.find((locale, index) => locales.indexOf(locale) !== index);
    if (duplicate)
        throw new SampleDocumentError(`${where} declares locale '${duplicate}' twice.`);
}
//# sourceMappingURL=sample-documents.js.map