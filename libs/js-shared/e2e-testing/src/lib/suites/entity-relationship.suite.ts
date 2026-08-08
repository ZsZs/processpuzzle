import { test, type Page } from '@playwright/test';
import * as fs from 'fs';
import type { BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { parentReferenceAttrName, relationshipTestersFor, type RelationshipControlTester } from '../controls/control-tester';
import { EntityCrudFixtureManager } from '../data/entity-crud-fixture-manager';
import { buildCreateDataForContext, buildUpdateDataForContext, identificationAttr } from '../data/test-data-factory';
import { EntityFormPO } from '../pages/entity-form.po';
import { EntityListPO } from '../pages/entity-list.po';
import { RelationshipFieldsetPO } from '../pages/relationship-fieldset.po';
import { RouteResolver } from '../routing/route.resolver';

export interface DefineEntityRelationshipSuiteOptions {
  /** Absolute path to the registry JSON produced by globalSetup. */
  registryPath: string;
  /** Route prefix shared by every entity list/detail URL, e.g. '/base-entity/samples'. */
  routePrefix: string;
  /** Timeout budget for each relationship test including fixture setup and cleanup. */
  timeoutMs?: number;
  /** Overrides Playwright's default expect timeout for form control assertions. */
  expectTimeoutMs?: number;
  /**
   * How many levels of embedded containment to walk. 2 covers a child that carries children of its own,
   * which is the case the nesting exists for; deeper structures repeat the same flow.
   */
  maxEmbeddedDepth?: number;
  /**
   * Relationships whose flow the consuming application's own business rules make unreachable with the
   * synthetic fixture data — an aggregate rule tying an owner's field to the sum of its rows, say, which no
   * generic value satisfies. The test is still registered, and skipped with the reason, so the gap stays
   * visible in the report rather than disappearing from it.
   */
  excludedRelationships?: ExcludedRelationship[];
}

/** One `<entity>.<attribute>` the suite is told not to exercise, and why. */
export interface ExcludedRelationship {
  entityName: string;
  attrName: string;
  reason: string;
}

/**
 * Registers one test per to-many relationship attribute, exercising what makes its control type different
 * from the other two.
 *
 * The generated CRUD suite cannot cover these: it fills a form and saves it, whereas a relationship is
 * established from the form of an entity that **already exists** — a component's foreign key points back at
 * its parent, and an embedded row has nowhere to be stored until the owner's document exists. So the flows
 * live here, and the assertions are chosen to fail if the three types were ever collapsed back into one:
 *
 * - `RELATED_ENTITIES` — the target is picked from its own list and outlives the reference: after removing a
 *   row it is **still in that list**.
 * - `COMPONENTS` — the child is created, read, edited and destroyed **through the owner's form alone**; its
 *   own fields take its own Save, the reference array takes the owner's, and removing a row destroys it.
 * - `EMBEDDED_COMPONENTS` — the same, one step further: the child has no endpoint at all, so its Save writes
 *   the owner's document, the owner's form never saves, and the child's screens hang below the owner's route.
 *
 * The two containment flows therefore never address the child's own URL. A component's list exists only as the
 * picker the owner's add button opens, and an embedded child has no list at all.
 */
export function defineEntityRelationshipSuite(options: DefineEntityRelationshipSuiteOptions): void {
  const registry: BaseEntityDescriptor[] = JSON.parse(fs.readFileSync(options.registryPath, 'utf-8'));
  const routes = new RouteResolver(options.routePrefix);
  const descriptorMap = new Map(registry.map((descriptor) => [descriptor.entityName, descriptor]));
  const timeoutMs = options.timeoutMs ?? 70_000;
  const maxEmbeddedDepth = options.maxEmbeddedDepth ?? 2;

  for (const descriptor of registry) {
    if (descriptor.isAbstract) continue;
    // An embedded entity is not reached on a route of its own; its relationships are exercised through the
    // recursion below, from the form of the entity containing it.
    if (descriptor.isEmbedded) continue;

    for (const tester of relationshipTestersFor(descriptor)) {
      const linkedName = tester.linkedEntityName();
      const linkedDescriptor = linkedName ? descriptorMap.get(linkedName) : undefined;

      // `RELATIONSHIP` keeps the E2E_SUITE grep of the consuming playwright.config working.
      test.describe(`[${descriptor.entityName}] RELATIONSHIP ${tester.attr.attrName} (${tester.kind})`, () => {
        test.describe.configure({ timeout: timeoutMs });

        let manager: EntityCrudFixtureManager | undefined;

        test.afterEach(async ({ page }) => {
          const managerForTest = manager;
          manager = undefined;
          await managerForTest?.testTearDown(page);
        });

        test(tester.kind, async ({ page }, testInfo) => {
          const excluded = excludedRelationship(options.excludedRelationships, descriptor.entityName, tester.attr.attrName);
          test.skip(excluded !== undefined, `excluded by the application: ${excluded?.reason}`);
          test.skip(linkedDescriptor === undefined, `'${linkedName}' is not in the registry`);
          test.skip(identificationAttr(descriptor) === undefined, `[${descriptor.entityName}] has no identification attr`);
          // A row is addressed by the text it shows, which is the child's identification attribute.
          test.skip(identificationAttr(linkedDescriptor as BaseEntityDescriptor) === undefined, `[${linkedName}] has no identification attr, so no row can be addressed`);

          const linked = linkedDescriptor as BaseEntityDescriptor;
          const suffix = uniqueSuffix(descriptor, tester, testInfo.retry);
          manager = new EntityCrudFixtureManager(routes, descriptorMap, suffix, options.expectTimeoutMs);

          const context = { page, routes, descriptorMap, manager, options, suffix };
          switch (tester.kind) {
            case 'RELATED_ENTITIES':
              await exerciseRelatedEntities(context, descriptor, linked, tester);
              break;
            case 'COMPONENTS':
              await exerciseComponents(context, descriptor, linked, tester);
              break;
            case 'EMBEDDED_COMPONENTS':
              await exerciseEmbeddedComponents(context, descriptor, tester, maxEmbeddedDepth);
              break;
          }
        });
      });
    }
  }
}

// region flow context
interface RelationshipTestContext {
  page: Page;
  routes: RouteResolver;
  descriptorMap: Map<string, BaseEntityDescriptor>;
  manager: EntityCrudFixtureManager;
  options: DefineEntityRelationshipSuiteOptions;
  suffix: string;
}

function excludedRelationship(exclusions: ExcludedRelationship[] | undefined, entityName: string, attrName: string): ExcludedRelationship | undefined {
  return exclusions?.find((exclusion) => exclusion.entityName === entityName && exclusion.attrName === attrName);
}

function uniqueSuffix(descriptor: BaseEntityDescriptor, tester: RelationshipControlTester, retry: number): string {
  const entitySegment = descriptor.entityName.replace(/\s+/g, '-').toLowerCase();
  return `e2e-${Date.now().toString(36)}-${entitySegment}-${tester.attr.attrName}-r${retry}`;
}

function formPO(context: RelationshipTestContext, descriptor: BaseEntityDescriptor): EntityFormPO {
  return new EntityFormPO(context.page, descriptor, context.routes, context.descriptorMap, { expectTimeoutMs: context.options.expectTimeoutMs });
}

function fieldsetPO(context: RelationshipTestContext, ownerDescriptor: BaseEntityDescriptor, tester: RelationshipControlTester): RelationshipFieldsetPO {
  return new RelationshipFieldsetPO(context.page, ownerDescriptor, tester, { expectTimeoutMs: context.options.expectTimeoutMs });
}
// endregion

// region RELATED_ENTITIES
/**
 * Association. Adding picks an entity that exists on its own, and removing the row detaches the reference —
 * the last assertion is the one that separates this control type from `COMPONENTS`.
 */
async function exerciseRelatedEntities(
  context: RelationshipTestContext,
  ownerDescriptor: BaseEntityDescriptor,
  linkedDescriptor: BaseEntityDescriptor,
  tester: RelationshipControlTester,
): Promise<void> {
  const { page, manager } = context;

  await manager.testSetup(page, ownerDescriptor);
  const owner = await manager.createEntity(page, ownerDescriptor);
  const target = await manager.createEntity(page, linkedDescriptor, `${context.suffix}-target`);

  const ownerForm = formPO(context, ownerDescriptor);
  const targetList = new EntityListPO(page, linkedDescriptor, context.routes);
  const fieldset = fieldsetPO(context, ownerDescriptor, tester);

  await ownerForm.navigateToDetail(owner.id);
  const rowsBefore = await fieldset.rows().count();

  await attachThroughSelectList(context, fieldset, targetList, target.displayValue);
  await fieldset.assertRow(target.displayValue);
  await fieldset.assertRowCount(rowsBefore + 1);

  // A reference is part of the owner's payload, so it takes the owner's save to persist.
  await ownerForm.save();
  await ownerForm.navigateToDetail(owner.id);
  await fieldset.assertRow(target.displayValue);

  await fieldset.removeRow(target.displayValue);
  await fieldset.assertNoRow(target.displayValue);
  await ownerForm.save();
  await ownerForm.navigateToDetail(owner.id);
  await fieldset.assertNoRow(target.displayValue);
  await fieldset.assertRowCount(rowsBefore);

  // Detaching is not deleting: the target outlives the reference to it.
  await targetList.navigateTo();
  await targetList.assertInList(target.displayValue);
}
// endregion

// region COMPONENTS
/**
 * Containment with the child in a table of its own.
 *
 * Every screen of the child is entered by clicking through the owner's form — a component belongs to one
 * parent, so its list is a picker the owner opens, not a place the user navigates to. Nothing here addresses
 * the child's own route, which is what keeps the flow honest for an application that never routes one.
 *
 * Where a save is effective is the other thing this asserts: the child's own form persists the child's
 * fields, while the reference array belongs to the owner's payload and takes the owner's Save. Removing a row
 * **destroys** the child, which is what separates this control type from `RELATED_ENTITIES`.
 */
async function exerciseComponents(
  context: RelationshipTestContext,
  ownerDescriptor: BaseEntityDescriptor,
  linkedDescriptor: BaseEntityDescriptor,
  tester: RelationshipControlTester,
): Promise<void> {
  const { page, descriptorMap, manager } = context;

  await manager.testSetup(page, ownerDescriptor);
  const owner = await manager.createEntity(page, ownerDescriptor);

  const ownerForm = formPO(context, ownerDescriptor);
  const childForm = formPO(context, linkedDescriptor);
  // The child's list, entered only as the picker the owner's add button opens.
  const picker = new EntityListPO(page, linkedDescriptor, context.routes);
  const fieldset = fieldsetPO(context, ownerDescriptor, tester);

  await ownerForm.navigateToDetail(owner.id);
  const ownerUrl = ownerForm.currentUrl();
  const rowsBefore = await fieldset.rows().count();

  const childSuffix = `${context.suffix}-component`;
  const childOptions = { uniqueSuffix: childSuffix, expectTimeoutMs: context.options.expectTimeoutMs };
  const childData = buildCreateDataForContext({ descriptor: linkedDescriptor, descriptorMap, uniqueSuffix: childSuffix });
  const childName = rowDisplayValue(linkedDescriptor, childData);
  // The foreign key back to the owner is left out of the fill: attaching the row is what stamps it, and
  // reaching the owner's picker from here would abandon the round trip already in progress.
  const parentRefAttr = parentReferenceAttrName(linkedDescriptor, ownerDescriptor.entityName);
  const ownerIdentification = parentRefAttr ? { [parentRefAttr]: owner.displayValue } : {};

  // Create: the picker's New opens the child's own form, and its Save reaches the child's own endpoint.
  await fieldset.clickAdd();
  await page.waitForURL(/\/list(\?|$)/);
  await picker.clickNew();
  await childForm.fillForm(childData, ownerIdentification, { skipLinked: true }, childOptions);
  await childForm.save();
  const child = manager.trackFixture(linkedDescriptor, childData);

  // Attach: the picker hands back a selection, never a freshly created entity, so the round trip is made
  // again — from the owner's form, as every step in this flow is.
  await page.goto(ownerUrl);
  await attachThroughSelectList(context, fieldset, picker, childName);
  await fieldset.assertRow(childName);
  await fieldset.assertRowCount(rowsBefore + 1);

  // The attribute holds ids, and they are part of the owner's payload: it takes the owner's Save to persist
  // them. The row text is resolved from the child's own store after the reload.
  await ownerForm.save();
  await ownerForm.navigateToDetail(owner.id);
  await fieldset.assertRow(childName);

  // Read back through the row, which is the only way in. Attaching stamped the owner into the child's foreign
  // key, so the containment holds from the child's side too.
  await fieldset.openRow(childName);
  await page.waitForURL(/\/details$/);
  child.id = entityIdFromDetailUrl(page.url());
  await childForm.assertFieldValues(childData, ownerIdentification, childOptions);

  // Edit: the change is made on the child's form, so the child's Save is what carries it — the owner's form
  // is only returned to. The identification attribute is left alone, so the row still addresses the same row.
  const updatedChildData = manager.buildUpdateData(linkedDescriptor, childData);
  await childForm.fillForm(updatedChildData, ownerIdentification, { skipLinked: true }, childOptions);
  await childForm.saveReturningTo(ownerUrl);
  child.data = updatedChildData;
  await fieldset.openRow(childName);
  await page.waitForURL(/\/details$/);
  await childForm.assertFieldValues(updatedChildData, ownerIdentification, childOptions);

  // Delete: destroys the child straight away, while detaching the row waits for the owner's Save.
  await page.goto(ownerUrl);
  await fieldset.removeRow(childName);
  await fieldset.assertNoRow(childName);
  await ownerForm.save();
  await ownerForm.navigateToDetail(owner.id);
  await fieldset.assertNoRow(childName);
  await fieldset.assertRowCount(rowsBefore);
  manager.markDeleted(child);

  // A component has no life outside its parent: it is gone from the only listing that ever showed it.
  await fieldset.clickAdd();
  await page.waitForURL(/\/list(\?|$)/);
  await picker.assertNotInList(childName);
  await picker.clickCancelButton();
}

/** The id in a `.../<entity>/<id>/details` URL — how a row's child identifies itself for cleanup. */
function entityIdFromDetailUrl(url: string): string {
  const segments = new URL(url).pathname.split('/');
  return segments[segments.length - 2];
}
// endregion

// region EMBEDDED_COMPONENTS
/**
 * Containment inside the owner's document. Rows are added, edited and deleted on routes nested below the
 * owner's form, and each of those actions writes the containing document — so no save on the owner's form
 * takes part in this flow, and a reload is what proves the row was persisted.
 */
async function exerciseEmbeddedComponents(
  context: RelationshipTestContext,
  ownerDescriptor: BaseEntityDescriptor,
  tester: RelationshipControlTester,
  maxEmbeddedDepth: number,
): Promise<void> {
  const { page, manager } = context;

  await manager.testSetup(page, ownerDescriptor);

  // The guard first: an unsaved owner has no document for a row to live in, so it offers no add button.
  const list = new EntityListPO(page, ownerDescriptor, context.routes);
  await list.navigateTo();
  await list.clickNew();
  await fieldsetPO(context, ownerDescriptor, tester).assertAddButtonHidden();

  const owner = await manager.createEntity(page, ownerDescriptor);
  const ownerForm = formPO(context, ownerDescriptor);
  await ownerForm.navigateToDetail(owner.id);

  await exerciseEmbeddedLevel(context, ownerDescriptor, ownerForm.currentUrl(), tester, [ownerDescriptor.entityName], 1, maxEmbeddedDepth);
}

/**
 * One level of embedded containment, then the same again for the child's own embedded attributes — a child
 * that carries children is the case the nesting exists for.
 */
async function exerciseEmbeddedLevel(
  context: RelationshipTestContext,
  ownerDescriptor: BaseEntityDescriptor,
  ownerUrl: string,
  tester: RelationshipControlTester,
  breadcrumbTrail: string[],
  depth: number,
  maxDepth: number,
): Promise<void> {
  const { page, routes, descriptorMap } = context;
  const childDescriptor = descriptorMap.get(tester.linkedEntityName() ?? '');
  if (!childDescriptor) return;

  const fieldset = fieldsetPO(context, ownerDescriptor, tester);
  const childForm = formPO(context, childDescriptor);
  const rowsBefore = await fieldset.rows().count();

  // Create: the child gets its own generated form, on a route below this one.
  await fieldset.clickAdd();
  await page.waitForURL(routes.embeddedDetailRoute(ownerUrl, childDescriptor, 'new'));

  const childData = buildCreateDataForContext({
    descriptor: childDescriptor,
    descriptorMap,
    uniqueSuffix: `${context.suffix}-d${depth}`,
  });
  const contextOptions = { uniqueSuffix: `${context.suffix}-d${depth}`, expectTimeoutMs: context.options.expectTimeoutMs };
  await childForm.fillForm(childData, {}, {}, contextOptions);
  await childForm.assertBreadcrumb([...breadcrumbTrail, childDescriptor.entityName], contextOptions);
  await childForm.saveReturningTo(ownerUrl);

  const rowName = rowDisplayValue(childDescriptor, childData);
  await fieldset.assertRow(rowName);
  await fieldset.assertRowCount(rowsBefore + 1);

  // Reload from the URL: the row has no endpoint of its own, so seeing it again proves the owner's document
  // carries it — and that a deep link to this level resolves.
  await page.goto(ownerUrl);
  await fieldset.assertRow(rowName);

  // Read back through the row's own form.
  await fieldset.openRow(rowName);
  await page.waitForURL((url) => url.toString().startsWith(`${ownerUrl}/`) && url.toString().endsWith('/details'));
  await childForm.assertFieldValues(childData, {}, contextOptions);

  // Edit: the change is made here, so the Save on this form is the effective one — and it writes the
  // containing document, which is why the owner's form never saves in this flow and the reload below is what
  // proves the edit was persisted. The identification attribute is left alone, so the row keeps its address.
  const updatedChildData = buildUpdateDataForContext({ descriptor: childDescriptor, descriptorMap, uniqueSuffix: `${context.suffix}-d${depth}` }, childData);
  await childForm.fillForm(updatedChildData, {}, {}, contextOptions);
  await childForm.saveReturningTo(ownerUrl);
  await page.goto(ownerUrl);
  await fieldset.openRow(rowName);
  await page.waitForURL((url) => url.toString().startsWith(`${ownerUrl}/`) && url.toString().endsWith('/details'));
  await childForm.assertFieldValues(updatedChildData, {}, contextOptions);
  const childUrl = childForm.currentUrl();

  if (depth < maxDepth) {
    for (const nestedTester of relationshipTestersFor(childDescriptor)) {
      if (nestedTester.kind !== 'EMBEDDED_COMPONENTS') continue;
      await exerciseEmbeddedLevel(context, childDescriptor, childUrl, nestedTester, [...breadcrumbTrail, childDescriptor.entityName], depth + 1, maxDepth);
    }
  }

  // Delete: reaches the containing document straight away, so the reload below is the persistence check.
  await page.goto(ownerUrl);
  await fieldset.removeRow(rowName);
  await fieldset.assertNoRow(rowName);
  await page.goto(ownerUrl);
  await fieldset.assertNoRow(rowName);
  await fieldset.assertRowCount(rowsBefore);
}

/** The text a row shows: the child's identification attribute, which is what `componentIdentification()` picks. */
function rowDisplayValue(childDescriptor: BaseEntityDescriptor, childData: Record<string, string>): string {
  const idAttr = identificationAttr(childDescriptor);
  if (!idAttr) throw new Error(`[${childDescriptor.entityName}] has no identification attr; an embedded row cannot be addressed`);

  return childData[idAttr.attrName];
}
// endregion

/**
 * The add button of a referencing control opens the target's list in select mode; picking a row returns to the
 * form that asked. Shared by both attach flows — a selection is the only thing the list hands back, even under
 * `NavigatorCommand.SELECT_OR_CREATE`, so an entity created there has to be picked on a second visit.
 */
async function attachThroughSelectList(
  context: RelationshipTestContext,
  fieldset: RelationshipFieldsetPO,
  targetList: EntityListPO,
  targetIdentification: string,
): Promise<void> {
  await fieldset.clickAdd();
  await context.page.waitForURL(/\/list(\?|$)/);
  await targetList.selectRowByIdentification(targetIdentification);
  await targetList.clickSelectButton();
  await context.page.waitForURL(/\/details$/);
}
