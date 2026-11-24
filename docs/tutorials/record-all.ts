import { readdir } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { recordTutorial, type Tutorial } from './tutorial-recorder';

/**
 * Usage:
 *   BASE_URL=https://staging.example.com TUTORIAL_USER=... TUTORIAL_PASSWORD=... \
 *     npx tsx record-all.ts [scenario-name] [--no-video]
 *
 * Picks up every *.tutorial.ts next to this script.
 */
async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const only = args.find((a) => !a.startsWith('--'));
  const video = !args.includes('--no-video');
  const dir = path.dirname(path.resolve(process.argv[1]));

  const files = (await readdir(dir)).filter((f) => f.endsWith('.tutorial.ts')).sort();
  const selected = only ? files.filter((f) => f.startsWith(only)) : files;
  if (selected.length === 0) {
    const matching = only ? ` matching "${only}"` : '';
    throw new Error(`No scenario found${matching} in ${dir}`);
  }

  for (const file of selected) {
    const mod = (await import(pathToFileURL(path.join(dir, file)).href)) as { default: Tutorial };
    await recordTutorial(mod.default, {
      baseUrl: process.env['BASE_URL'] ?? 'http://localhost:4200',
      outDir: path.join(dir, 'out'),
      video,
    });
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
