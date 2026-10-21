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

const attribute = (line, name) => new RegExp(` ${name}="([^"]*)"`).exec(line)?.[1];

let found = 0;
for (const module of new Set(modules)) {
  const report = path.join(root, module, 'target', 'pmd.xml');
  if (!existsSync(report)) continue;
  let file;
  let violation;
  for (const line of readFileSync(report, 'utf8').split('\n')) {
    if (line.startsWith('<file ')) {
      file = path.resolve(attribute(line, 'name'));
    } else if (line.startsWith('<violation ') && changed.has(file)) {
      violation = { line: attribute(line, 'beginline'), rule: attribute(line, 'rule'), method: attribute(line, 'method'), message: '' };
    } else if (violation && line.startsWith('</violation>')) {
      const method = violation.method ? ' (' + violation.method + ')' : '';
      console.log(path.relative(root, file) + ':' + violation.line + '  ' + violation.rule + method + '  ' + violation.message.trim());
      found++;
      violation = undefined;
    } else if (violation) {
      violation.message += line.replace(/^.*?>/, '') + ' ';
    }
  }
}
process.exit(found > 0 ? 1 : 0);
