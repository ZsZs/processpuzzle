// Prints the PMD violations (target/pmd.xml of each module) that fall in files listed in <changed-files-list>
// and exits 1 if there are any. Used by pre-push-check.sh so a module's existing violations never block a push.
// Usage: node pmd-changed-violations.mjs <changed-files-list> <module-dir>...
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const [listFile, ...modules] = process.argv.slice(2);
const root = process.cwd();
const changed = new Set(
  readFileSync(listFile, 'utf8')
    .split('\n')
    .filter(Boolean)
    .map((file) => path.resolve(root, file)),
);

let found = 0;
for (const module of new Set(modules)) {
  const report = path.join(root, module, 'target', 'pmd.xml');
  if (!existsSync(report)) continue;
  for (const [, name, body] of readFileSync(report, 'utf8').matchAll(/<file name="([^"]+)">([\s\S]*?)<\/file>/g)) {
    if (!changed.has(path.resolve(name))) continue;
    for (const [, line, rule, method, message] of body.matchAll(/<violation beginline="(\d+)"[^>]*? rule="([^"]+)"[^>]*?(?:method="([^"]*)")?[^>]*>\s*([\s\S]*?)\s*<\/violation>/g)) {
      console.log(`${path.relative(root, name)}:${line}  ${rule}${method ? ` (${method})` : ''}  ${message.trim()}`);
      found++;
    }
  }
}
process.exit(found > 0 ? 1 : 0);
