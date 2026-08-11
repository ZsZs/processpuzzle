import { expect, test, type Page } from '@playwright/test';
import * as fs from 'node:fs';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { artifactTestersFor, type ArtifactControlTester } from '../controls/control-tester';
import { createPngBuffer, createTextBuffer } from '../data/binary-fixtures';
import { EntityCrudFixtureManager } from '../data/entity-crud-fixture-manager';
import { identificationAttr } from '../data/test-data-factory';
import { ArtifactFieldsetPO, type ArtifactUpload } from '../pages/artifact-fieldset.po';
import { EntityFormPO } from '../pages/entity-form.po';
import { RouteResolver } from '../routing/route.resolver';

export interface DefineEntityArtifactSuiteOptions {
  /** Absolute path to the registry JSON produced by globalSetup. */
  registryPath: string;
  /** Route prefix shared by every entity list/detail URL, e.g. '/base-entity/samples'. */
  routePrefix: string;
  /** Timeout budget for each artifact test including fixture setup and cleanup. */
  timeoutMs?: number;
  /** Overrides Playwright's default expect timeout — an upload is a round trip to the object store. */
  expectTimeoutMs?: number;
  /** `<entity>.<attribute>` pairs the consuming application's own rules make unreachable, and why. */
  excludedArtifacts?: ExcludedArtifact[];
}

/** One `<entity>.<attribute>` the suite is told not to exercise, and why. */
export interface ExcludedArtifact {
  entityName: string;
  attrName: string;
  reason: string;
}

/** MIME icon `ArtifactComponent` shows for a `text/*` artifact — the non-image branch of its lookup table. */
const TEXT_MIME_ICON = 'article';

/**
 * Registers one test per `ARTIFACT` attribute, exercising the round trip through the object store.
 *
 * This is the one generated suite whose subject is not the entity: the value the control holds is a reference,
 * and the thing referred to lives in a store the entity's own endpoint knows nothing about. So the assertions
 * are chosen to fail if the reference and the object ever came apart —
 *
 * - **Upload** puts an object in the store, and the row that appears names it.
 * - **A refused upload says so** rather than closing the selector as a cancel would, which is the only thing
 *   that tells an unreachable store from a user who changed their mind.
 * - **The reference travels in the owner's payload**, so it takes the owner's Save, and a reload proves it.
 * - **The link resolves to the stored bytes** — the URI the control opens serves back exactly what was
 *   uploaded, which is what tells a working store from a form field holding a plausible-looking object id.
 * - **Delete reaches the store**, not just the form: the URI stops serving.
 * - **A raster image gets a thumbnail** and anything else a MIME icon, which is the one place the control
 *   depends on the store having derived something rather than merely kept what it was given.
 *
 * Every one of those holds for both object-store adapters — `processpuzzle-store` over MinIO and the
 * `objectStore` Cloud Function over Firebase Storage — so the same suite is what keeps the two interchangeable.
 */
export function defineEntityArtifactSuite(options: DefineEntityArtifactSuiteOptions): void {
  const registry: BaseEntityDescriptor[] = JSON.parse(fs.readFileSync(options.registryPath, 'utf-8'));
  const routes = new RouteResolver(options.routePrefix);
  const descriptorMap = new Map(registry.map((descriptor) => [descriptor.entityName, descriptor]));
  const timeoutMs = options.timeoutMs ?? 90_000;

  for (const descriptor of registry) {
    if (descriptor.isAbstract) continue;
    // An embedded entity has no route of its own; its form is reached from the entity containing it, which is
    // the relationship suite's flow rather than this one's.
    if (descriptor.isEmbedded) continue;

    for (const tester of artifactTestersFor(descriptor)) {
      // `ARTIFACT` keeps the E2E_SUITE grep of the consuming playwright.config working.
      test.describe(`[${descriptor.entityName}] ARTIFACT ${tester.attr.attrName}`, () => {
        test.describe.configure({ timeout: timeoutMs });

        let manager: EntityCrudFixtureManager | undefined;

        test.afterEach(async ({ page }) => {
          const managerForTest = manager;
          manager = undefined;
          await managerForTest?.testTearDown(page);
        });

        test('ARTIFACT', async ({ page }, testInfo) => {
          const excluded = options.excludedArtifacts?.find((entry) => entry.entityName === descriptor.entityName && entry.attrName === tester.attr.attrName);
          test.skip(excluded !== undefined, `excluded by the application: ${excluded?.reason}`);
          test.skip(identificationAttr(descriptor) === undefined, `[${descriptor.entityName}] has no identification attr`);

          const entitySegment = descriptor.entityName.replace(/\s+/g, '-').toLowerCase();
          const suffix = `e2e-${Date.now().toString(36)}-${entitySegment}-${tester.attr.attrName}-r${testInfo.retry}`;
          manager = new EntityCrudFixtureManager(routes, descriptorMap, suffix, options.expectTimeoutMs);

          await manager.testSetup(page, descriptor);
          const owner = await manager.createEntity(page, descriptor);

          const form = new EntityFormPO(page, descriptor, routes, descriptorMap, { expectTimeoutMs: options.expectTimeoutMs });
          const fieldset = new ArtifactFieldsetPO(page, descriptor, tester, { expectTimeoutMs: options.expectTimeoutMs });

          await form.navigateToDetail(owner.id);
          await fieldset.assertNoArtifact();
          await fieldset.assertSelectorRevealedOnFocus();

          await fieldset.assertUploadFailureIsReported({ name: `${suffix}-rejected.txt`, mimeType: 'text/plain', buffer: createTextBuffer(`rejected fixture ${suffix}`) });
          await exerciseImageArtifact(page, form, fieldset, tester, owner.id, suffix);
          await exerciseNonImageArtifact(form, fieldset, suffix);
        });
      });
    }
  }
}

/**
 * The full lifecycle, with an image — the payload that makes the store derive something of its own.
 */
async function exerciseImageArtifact(
  page: Page,
  form: EntityFormPO,
  fieldset: ArtifactFieldsetPO,
  tester: ArtifactControlTester,
  ownerId: string,
  suffix: string,
): Promise<void> {
  const image: ArtifactUpload = { name: `${suffix}.png`, mimeType: 'image/png', buffer: createPngBuffer() };

  await fieldset.uploadFile(image);
  if (tester.showsThumbnailFor(image.mimeType)) await fieldset.assertThumbnail();

  // The reference is part of the owner's payload, so it takes the owner's Save to persist — and the reload is
  // what proves the row came back from the entity rather than from the component's own state.
  await form.save();
  await form.navigateToDetail(ownerId);
  await fieldset.assertArtifact(image.name);
  if (tester.showsThumbnailFor(image.mimeType)) await fieldset.assertThumbnail();

  // The link resolves through the store to the object it named: the URI serves back the bytes uploaded.
  const uri = await fieldset.openArtifact(image.name);
  expect(uri, 'the artifact link opened a tab with no resolved URI').toMatch(/^https?:\/\//);
  const served = await page.request.get(uri);
  expect(served.status(), `the artifact URI did not serve the object: ${uri}`).toBe(200);
  expect((await served.body()).length).toBe(image.buffer.length);

  // Delete reaches the store before it clears the reference, so the URI stops serving. Only the object is
  // asserted: `processpuzzle-store` leaves the thumbnail behind, where the Cloud Function cascades to it, and a
  // suite that has to pass on both adapters can only claim what both do.
  await fieldset.removeArtifact(image.name);
  await fieldset.assertNoArtifact();
  expect((await page.request.get(uri)).ok(), 'the object outlived the artifact it was attached to').toBe(false);

  await form.save();
  await form.navigateToDetail(ownerId);
  await fieldset.assertNoArtifact();
}

/** The other branch of the control's rendering: no thumbnail is derived, so it stands for the type instead. */
async function exerciseNonImageArtifact(form: EntityFormPO, fieldset: ArtifactFieldsetPO, suffix: string): Promise<void> {
  const document: ArtifactUpload = { name: `${suffix}.txt`, mimeType: 'text/plain', buffer: createTextBuffer(`artifact fixture ${suffix}`) };

  await fieldset.uploadFile(document);
  await fieldset.assertMimeIcon(TEXT_MIME_ICON);

  await fieldset.removeArtifact(document.name);
  await fieldset.assertNoArtifact();
  // Saved so the entity is left clean for the teardown that deletes it.
  await form.save();
}
