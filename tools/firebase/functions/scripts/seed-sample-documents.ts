import { initializeApp } from 'firebase-admin/app';
import { listSampleDocumentFiles, readSampleDocumentFile, resolveSampleDocumentsDir, SampleDocumentError, seedSampleDocuments } from '../src/base-document/sample-documents.js';
import { FirestoreDocumentStore } from '../src/base-document/document-store.js';

/**
 * Seeds `base-document-backend`'s sample documents into a project's Firestore. Run from the deploy
 * workflow, not from a function — see the module comment of `sample-documents.ts`.
 *
 *     node lib/scripts/seed-sample-documents.js --project <projectId> [--reset] [--org <orgKey>] [--dir <path>]
 *
 * `--project` is required rather than inferred. Application default credentials plus an ambient
 * `GOOGLE_CLOUD_PROJECT` would let a mistake elsewhere in the workflow point a `--reset` run at the
 * wrong environment, and this is the one script here that deletes data.
 */
interface Options {
  projectId: string;
  reset: boolean;
  orgKeys: string[];
  directory?: string;
}

async function main(): Promise<void> {
  const options = parseArguments(process.argv.slice(2));
  const directory = options.directory ?? resolveSampleDocumentsDir();
  const files = listSampleDocumentFiles(directory).filter((name) => options.orgKeys.length === 0 || options.orgKeys.some((orgKey) => name.startsWith(`${orgKey}-`)));

  if (files.length === 0) throw new SampleDocumentError(`No sample document files to seed in '${directory}'${options.orgKeys.length > 0 ? ` for ${options.orgKeys.join(', ')}` : ''}.`);

  // Before the store's own lazy `initializeApp()`, so that the project is this script's argument
  // rather than whatever the environment happens to say.
  initializeApp({ projectId: options.projectId });
  const store = new FirestoreDocumentStore();

  console.log(`Seeding ${files.length} sample document file(s) from '${directory}' into '${options.projectId}'${options.reset ? ', resetting each organization first' : ''}.`);

  for (const fileName of files) {
    const file = readSampleDocumentFile(directory, fileName);
    const outcome = await seedSampleDocuments(store, file, { reset: options.reset });
    console.log(`  ${fileName}: deleted ${outcome.deleted}, imported ${outcome.imported.length} [${outcome.imported.join(', ')}], skipped ${outcome.skipped.length} [${outcome.skipped.join(', ')}]`);
  }
}

function parseArguments(argv: readonly string[]): Options {
  const options: Options = { projectId: '', reset: false, orgKeys: [] };

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
        throw new SampleDocumentError(`Unknown argument '${argument}'.`);
    }
  }

  if (!options.projectId) throw new SampleDocumentError("'--project <projectId>' is required.");
  return options;
}

function requireValue(argv: readonly string[], index: number, argument: string): string {
  const value = argv[index];
  if (value === undefined || value.startsWith('--')) throw new SampleDocumentError(`'${argument}' needs a value.`);
  return value;
}

main().catch((error: unknown) => {
  console.error(error instanceof SampleDocumentError ? error.message : error);
  process.exitCode = 1;
});
