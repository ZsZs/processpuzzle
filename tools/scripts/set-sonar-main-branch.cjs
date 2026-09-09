#!/usr/bin/env node
/*
 * Sets the main (default) branch of every SonarCloud project in this workspace.
 *
 * Project keys are discovered from the sonar-project.properties files, so the
 * script stays in sync as libraries are added.
 *
 * Usage (from the workspace root):
 *   SONAR_TOKEN=... node tools/scripts/set-sonar-main-branch.cjs [branch]           # dry run
 *   SONAR_TOKEN=... node tools/scripts/set-sonar-main-branch.cjs [branch] --apply
 *   SONAR_TOKEN=... node tools/scripts/set-sonar-main-branch.cjs [branch] --apply --delete-existing
 *
 * `branch` defaults to "develop".
 *
 * SonarCloud cannot rename the main branch onto a name that is already taken by
 * another analysed branch. --delete-existing removes that conflicting branch
 * first, which permanently discards its analysis history on SonarCloud.
 */
const fs = require('fs');
const path = require('path');

const HOST = 'https://sonarcloud.io';
const args = process.argv.slice(2);
const APPLY = args.includes('--apply');
const DELETE_EXISTING = args.includes('--delete-existing');
const TARGET = args.find((a) => !a.startsWith('--')) || 'develop';

// Prefer the env var (matches run-sonar-scanner.cjs and CI); fall back to a
// gitignored .sonar-token file at the workspace root so the token never has to
// be pasted onto a command line.
const tokenFile = path.join(process.cwd(), '.sonar-token');
const token = process.env.SONAR_TOKEN || (fs.existsSync(tokenFile) ? fs.readFileSync(tokenFile, 'utf8').trim() : null);
if (!token) {
  console.error('set-sonar-main-branch: no token — set SONAR_TOKEN or create a .sonar-token file in the workspace root');
  process.exit(1);
}
const AUTH = { Authorization: `Bearer ${token}` };

function findPropertiesFiles(dir, found = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'target' || entry.name === 'dist' || entry.name.startsWith('.')) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) findPropertiesFiles(full, found);
    else if (entry.name === 'sonar-project.properties') found.push(full);
  }
  return found;
}

function readProjectKey(file) {
  const match = /^\s*sonar\.projectKey\s*=\s*(.+?)\s*$/m.exec(fs.readFileSync(file, 'utf8'));
  return match ? match[1] : null;
}

async function api(endpoint, params, method = 'GET') {
  const url = new URL(`${HOST}/api/${endpoint}`);
  const body = new URLSearchParams(params);
  const init = { method, headers: { ...AUTH } };
  if (method === 'GET') url.search = body.toString();
  else {
    init.headers['Content-Type'] = 'application/x-www-form-urlencoded';
    init.body = body.toString();
  }
  const response = await fetch(url, init);
  const text = await response.text();
  if (!response.ok) {
    let message = text;
    try {
      message = JSON.parse(text).errors?.map((e) => e.msg).join('; ') || text;
    } catch {
      /* keep the raw body */
    }
    throw new Error(`${response.status} ${message}`);
  }
  return text ? JSON.parse(text) : {};
}

(async () => {
  const projects = findPropertiesFiles(process.cwd())
    .map((file) => ({ file: path.relative(process.cwd(), file), key: readProjectKey(file) }))
    .filter((p) => p.key)
    .sort((a, b) => a.key.localeCompare(b.key));

  console.log(`${APPLY ? 'Applying' : 'Dry run —'} main branch "${TARGET}" for ${projects.length} project(s)\n`);

  const summary = { renamed: 0, alreadyOk: 0, blocked: 0, failed: 0 };

  for (const project of projects) {
    let branches;
    try {
      ({ branches } = await api('project_branches/list', { project: project.key }));
    } catch (error) {
      console.log(`  ✗ ${project.key}: cannot list branches — ${error.message}`);
      summary.failed++;
      continue;
    }

    const main = branches.find((b) => b.isMain);
    const conflict = branches.find((b) => !b.isMain && b.name === TARGET);
    const others = branches.filter((b) => !b.isMain).map((b) => b.name);
    const detail = `main="${main ? main.name : '?'}"${others.length ? `, other=[${others.join(', ')}]` : ''}`;

    if (main && main.name === TARGET) {
      console.log(`  = ${project.key}: already "${TARGET}"${others.length ? ` (${others.join(', ')})` : ''}`);
      summary.alreadyOk++;
      continue;
    }

    if (conflict && !DELETE_EXISTING) {
      console.log(`  ! ${project.key}: ${detail} — "${TARGET}" already exists as a separate branch; rerun with --delete-existing to drop it first`);
      summary.blocked++;
      continue;
    }

    if (!APPLY) {
      console.log(`  → ${project.key}: ${detail} — would ${conflict ? `delete branch "${TARGET}" then ` : ''}rename main to "${TARGET}"`);
      summary.renamed++;
      continue;
    }

    try {
      if (conflict) await api('project_branches/delete', { project: project.key, branch: TARGET }, 'POST');
      await api('project_branches/rename', { project: project.key, name: TARGET }, 'POST');
      console.log(`  ✓ ${project.key}: main renamed "${main ? main.name : '?'}" → "${TARGET}"${conflict ? ' (dropped duplicate branch)' : ''}`);
      summary.renamed++;
    } catch (error) {
      console.log(`  ✗ ${project.key}: ${detail} — ${error.message}`);
      summary.failed++;
    }
  }

  console.log(
    `\n${APPLY ? 'Changed' : 'Would change'}: ${summary.renamed} | already "${TARGET}": ${summary.alreadyOk} | blocked: ${summary.blocked} | failed: ${summary.failed}`,
  );
  if (!APPLY) console.log('Rerun with --apply to make the changes.');
  process.exit(summary.failed ? 1 : 0);
})();
