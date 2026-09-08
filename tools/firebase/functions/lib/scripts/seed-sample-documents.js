"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const app_1 = require("firebase-admin/app");
const sample_documents_js_1 = require("../src/base-document/sample-documents.js");
const document_store_js_1 = require("../src/base-document/document-store.js");
async function main() {
    var _a;
    const options = parseArguments(process.argv.slice(2));
    const directory = (_a = options.directory) !== null && _a !== void 0 ? _a : (0, sample_documents_js_1.resolveSampleDocumentsDir)();
    const files = (0, sample_documents_js_1.listSampleDocumentFiles)(directory).filter((name) => options.orgKeys.length === 0 || options.orgKeys.some((orgKey) => name.startsWith(`${orgKey}-`)));
    if (files.length === 0)
        throw new sample_documents_js_1.SampleDocumentError(`No sample document files to seed in '${directory}'${options.orgKeys.length > 0 ? ` for ${options.orgKeys.join(', ')}` : ''}.`);
    // Before the store's own lazy `initializeApp()`, so that the project is this script's argument
    // rather than whatever the environment happens to say.
    (0, app_1.initializeApp)({ projectId: options.projectId });
    const store = new document_store_js_1.FirestoreDocumentStore();
    console.log(`Seeding ${files.length} sample document file(s) from '${directory}' into '${options.projectId}'${options.reset ? ', resetting each organization first' : ''}.`);
    for (const fileName of files) {
        const file = (0, sample_documents_js_1.readSampleDocumentFile)(directory, fileName);
        const outcome = await (0, sample_documents_js_1.seedSampleDocuments)(store, file, { reset: options.reset });
        console.log(`  ${fileName}: deleted ${outcome.deleted}, imported ${outcome.imported.length} [${outcome.imported.join(', ')}], skipped ${outcome.skipped.length} [${outcome.skipped.join(', ')}]`);
    }
}
function parseArguments(argv) {
    const options = { projectId: '', reset: false, orgKeys: [] };
    for (let index = 0; index < argv.length; index++) {
        const argument = argv[index];
        switch (argument) {
            case '--reset':
                options.reset = true;
                break;
            case '--project':
                options.projectId = requireValue(argv, ++index, argument);
                break;
            case '--org':
                options.orgKeys.push(requireValue(argv, ++index, argument));
                break;
            case '--dir':
                options.directory = requireValue(argv, ++index, argument);
                break;
            default:
                throw new sample_documents_js_1.SampleDocumentError(`Unknown argument '${argument}'.`);
        }
    }
    if (!options.projectId)
        throw new sample_documents_js_1.SampleDocumentError("'--project <projectId>' is required.");
    return options;
}
function requireValue(argv, index, argument) {
    const value = argv[index];
    if (value === undefined || value.startsWith('--'))
        throw new sample_documents_js_1.SampleDocumentError(`'${argument}' needs a value.`);
    return value;
}
main().catch((error) => {
    console.error(error instanceof sample_documents_js_1.SampleDocumentError ? error.message : error);
    process.exitCode = 1;
});
//# sourceMappingURL=seed-sample-documents.js.map