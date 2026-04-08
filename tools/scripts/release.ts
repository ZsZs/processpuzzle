// tools/scripts/release.ts
import { releaseChangelog, releasePublish, releaseVersion } from 'nx/release';
import { execSync } from 'child_process';

async function main() {
  // Step 1 — bump versions in package.json AND pom.xml
  const { workspaceVersion, projectsVersionData } = await releaseVersion({
    specifier: process.env.RELEASE_TYPE ?? 'patch',
  });

  // Step 2 — update pom.xml versions to match
  const version = workspaceVersion;
  execSync(`mvn versions:set -DnewVersion=${version} -DautoVersionSubmodules=true`, { stdio: 'inherit' });
  execSync(`mvn versions:commit`, { stdio: 'inherit' });

  // Step 3 — generate unified changelog
  await releaseChangelog({ version, interactive: false });

  // Step 4 — publish JS packages to npm
  await releasePublish({ dryRun: false });

  // Step 5 — publish Java JARs to Maven Central
  execSync(`mvn deploy -P release --no-transfer-progress`, { stdio: 'inherit' });

  console.log(`✅ Released version ${version} to npm + Maven Central`);
}

main().catch(console.error);
