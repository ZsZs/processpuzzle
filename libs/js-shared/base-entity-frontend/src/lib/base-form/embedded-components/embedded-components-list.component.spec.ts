import { ANIMATION_MODULE_TYPE, InjectionToken, Provider, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mock, type MockProxy } from 'vitest-mock-extended';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../../base-entity/base-entity.descriptor';
import { EmbeddedRow } from '../../base-entity-embedded/embedded-aggregate';
import type { EmbeddedBreadcrumbLevel } from '../../base-entity-embedded/embedded-route-context';
import { BASE_ENTITY_FACADE_REGISTRY } from '../../base-entity-facade/base-entity-facade-registry';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { EmbeddedComponentsListComponent } from './embedded-components-list.component';

const PARENT_ENTITY_NAME = 'TestEntity';
const EMBEDDED_ENTITY_NAME = 'EmbeddedComponent';

function makeConfig(referenceIdField?: string, ordered = false): BaseEntityAttrDescriptor {
  const config = new BaseEntityAttrDescriptor('embeddedComponents', FormControlType.EMBEDDED_COMPONENTS, 'Embedded Components');
  config.linkedEntityType = EMBEDDED_ENTITY_NAME;
  if (referenceIdField) config.referenceIdField = referenceIdField;
  config.ordered = ordered;
  return config;
}

function makeEmbeddedDescriptor({ componentParent = PARENT_ENTITY_NAME, isEmbedded = true }: { componentParent?: string; isEmbedded?: boolean } = {}): BaseEntityDescriptor {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, undefined, undefined, true);
  return new BaseEntityDescriptor({
    attrDescriptors: [nameAttr],
    entityName: EMBEDDED_ENTITY_NAME,
    entityTitle: EMBEDDED_ENTITY_NAME,
    componentParent,
    isEmbedded,
  });
}

/** Stands in for the child's store, whose rows come from the containing entity's payload. */
function makeEmbeddedStore(rows: EmbeddedRow[] = []) {
  return {
    entities: signal(rows),
    load: vi.fn(),
    delete: vi.fn(() => Promise.resolve()),
  };
}

function provideEmbeddedFacade(descriptor: BaseEntityDescriptor | undefined, store: unknown): Provider[] {
  if (!descriptor) return [{ provide: BASE_ENTITY_FACADE_REGISTRY, useValue: {} }];

  const facadeToken = new InjectionToken<unknown>('EMBEDDED_FACADE');
  return [
    { provide: facadeToken, useValue: { descriptor, store } },
    { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { [EMBEDDED_ENTITY_NAME]: facadeToken } },
  ];
}

async function setupList({
  config = makeConfig(),
  descriptor = makeEmbeddedDescriptor(),
  registered = true,
  store = makeEmbeddedStore(),
  ownerPersisted = true,
  breadcrumb = [],
  navigator,
  dialog,
}: {
  config?: BaseEntityAttrDescriptor;
  descriptor?: BaseEntityDescriptor;
  /** False leaves the embedded entity out of the facade registry. */
  registered?: boolean;
  store?: ReturnType<typeof makeEmbeddedStore>;
  /** False models an owner the user has not saved yet, which therefore has no id. */
  ownerPersisted?: boolean;
  /** What the URL says about the owner; empty leaves the entity's own id as the only evidence. */
  breadcrumb?: EmbeddedBreadcrumbLevel[];
  navigator?: Partial<Record<'navigateToEmbedded', unknown>>;
  dialog?: MockProxy<MatDialog>;
} = {}) {
  const entity = new TestEntity('parent-1', 'Parent');
  // TestEntity mints an id of its own, so an unsaved owner has to have it cleared.
  if (!ownerPersisted) Reflect.set(entity, 'id', undefined);
  const providers: Provider[] = [...provideEmbeddedFacade(registered ? descriptor : undefined, store)];
  // The stub stands in for the whole navigator, so it has to carry the breadcrumb the control reads as well.
  if (navigator) providers.push({ provide: BaseFormNavigatorSingletonStore, useValue: { breadcrumb: signal(breadcrumb), ...navigator } });
  if (dialog) providers.push({ provide: MatDialog, useValue: dialog });

  const harness = await setupFormControlTest(EmbeddedComponentsListComponent, config, entity, providers);
  return { ...harness, entity, store, component: harness.component as EmbeddedComponentsListComponent<TestEntity> };
}

describe('EmbeddedComponentsListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ providers: [{ provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' }] }).compileComponents();
  });

  describe('containment contract', () => {
    it('throws when the embedded entity is not registered', async () => {
      await expect(setupList({ registered: false })).rejects.toThrow(/not registered in BASE_ENTITY_FACADE_REGISTRY/);
    });

    it('throws when the child does not name this entity as its componentParent', async () => {
      await expect(setupList({ descriptor: makeEmbeddedDescriptor({ componentParent: 'OtherEntity' }) })).rejects.toThrow(/does not name 'TestEntity' as its componentParent/);
    });

    it('throws when the child is not embedded, because that is a different control type', async () => {
      await expect(setupList({ descriptor: makeEmbeddedDescriptor({ componentParent: PARENT_ENTITY_NAME, isEmbedded: false }) })).rejects.toThrow(/has to use FormControlType.COMPONENTS/);
    });
  });

  describe('rows()', () => {
    /**
     * The child's store is already scoped to this owner — it resolves the rows out of the containing
     * document — so the list shows what the store holds rather than re-deriving it from the form value.
     */
    it('shows the rows the child store holds', async () => {
      const store = makeEmbeddedStore([{ id: 'a', name: 'first' }]);
      const { component } = await setupList({ store });

      expect(component.rows()).toEqual([{ id: 'a', name: 'first' }]);
    });

    it('re-reads the rows on render, because the store is shared by every owner of this child type', async () => {
      const store = makeEmbeddedStore();
      await setupList({ store });

      expect(store.load).toHaveBeenCalled();
    });

    it('follows the store as rows are added and removed', async () => {
      const store = makeEmbeddedStore([{ id: 'a' }]);
      const { component } = await setupList({ store });

      store.entities.set([{ id: 'a' }, { id: 'b' }]);

      expect(component.rows()).toHaveLength(2);
    });
  });

  describe('displayName()', () => {
    it('uses the child’s identifying attribute', async () => {
      const { component } = await setupList({ store: makeEmbeddedStore([{ id: 'a', name: 'first' }]) });

      expect(component.displayName({ id: 'a', name: 'first' }, 0)).toBe('first');
    });

    it('falls back to the key when the identifying attribute is empty', async () => {
      const { component } = await setupList();

      expect(component.displayName({ id: 'a' }, 0)).toBe('a');
    });

    // App Region has no `id`; `type` is what identifies it, and so what the URL segment carries.
    it('falls back to the referenceIdField for a child with no id', async () => {
      const { component } = await setupList({ config: makeConfig('type') });

      expect(component.displayName({ type: 'sidenav' }, 0)).toBe('sidenav');
    });

    it('falls back to the position when nothing identifies the row', async () => {
      const { component } = await setupList();

      expect(component.displayName({}, 1)).toBe(`${EMBEDDED_ENTITY_NAME} 2`);
    });
  });

  describe('navigation', () => {
    it('opens a row on its own form, below this one', async () => {
      const navigateToEmbedded = vi.fn();
      const { component } = await setupList({ navigator: { navigateToEmbedded } });

      component.openComponent({ id: 'a' });

      expect(navigateToEmbedded).toHaveBeenCalledWith(EMBEDDED_ENTITY_NAME, 'a');
    });

    it('creates a child on the same form, addressed as new', async () => {
      const navigateToEmbedded = vi.fn();
      const { component } = await setupList({ navigator: { navigateToEmbedded } });

      component.addComponent();

      expect(navigateToEmbedded).toHaveBeenCalledWith(EMBEDDED_ENTITY_NAME, 'new');
    });

    /** An embedded row is stored inside its owner, so there is nowhere to put one until the owner exists. */
    it('refuses to add a child to an owner that has not been saved yet', async () => {
      const navigateToEmbedded = vi.fn();
      const { component } = await setupList({ ownerPersisted: false, navigator: { navigateToEmbedded } });

      expect(component.ownerIsPersisted()).toBe(false);
      component.addComponent();
      expect(navigateToEmbedded).not.toHaveBeenCalled();
    });

    /**
     * The id is no evidence: an entity type that mints one at construction — `TestEntity` does — would
     * otherwise make every unsaved owner look persisted, and the guard above would never fire in an
     * application at all. The route is what says whether a document exists to store a row in.
     */
    it('refuses an owner the route addresses as new, however the entity came by its id', async () => {
      const navigateToEmbedded = vi.fn();
      const { component } = await setupList({
        breadcrumb: [{ entityName: PARENT_ENTITY_NAME, entityId: 'new', url: '', baseUrl: '' }],
        navigator: { navigateToEmbedded },
      });

      expect(component.ownerIsPersisted()).toBe(false);
      component.addComponent();
      expect(navigateToEmbedded).not.toHaveBeenCalled();
    });

    it('accepts an owner the route names by id', async () => {
      const { component } = await setupList({
        breadcrumb: [{ entityName: PARENT_ENTITY_NAME, entityId: 'parent-1', url: '', baseUrl: '' }],
        navigator: { navigateToEmbedded: vi.fn() },
      });

      expect(component.ownerIsPersisted()).toBe(true);
    });
  });

  describe('deleteComponent()', () => {
    it('deletes through the child store once the deletion is confirmed', async () => {
      const dialog = mock<MatDialog>();
      dialog.open.mockReturnValue({ afterClosed: () => of(true) } as never);
      const store = makeEmbeddedStore([{ id: 'a', name: 'first' }]);
      const { component } = await setupList({ store, dialog });

      component.deleteComponent({ id: 'a', name: 'first' });

      expect(store.delete).toHaveBeenCalledWith('a');
    });

    it('leaves the row alone when the deletion is cancelled', async () => {
      const dialog = mock<MatDialog>();
      dialog.open.mockReturnValue({ afterClosed: () => of(false) } as never);
      const store = makeEmbeddedStore([{ id: 'a' }]);
      const { component } = await setupList({ store, dialog });

      component.deleteComponent({ id: 'a' });

      expect(store.delete).not.toHaveBeenCalled();
    });
  });

  describe('reordering', () => {
    const threeRows = () => makeEmbeddedStore([{ id: 'a' }, { id: 'b' }, { id: 'c' }]);
    const query = (fixture: { nativeElement: HTMLElement }, testId: string) => [...fixture.nativeElement.querySelectorAll<HTMLButtonElement>(`[data-testid="${testId}"]`)];

    it('offers no reordering unless the attribute is ordered', async () => {
      const { component, fixture } = await setupList({ store: threeRows() });

      expect(component.reorderable()).toBe(false);
      expect(query(fixture, 'embedded-row-move-up')).toHaveLength(0);
      expect(query(fixture, 'embedded-row-drag-handle')).toHaveLength(0);
    });

    it('offers a handle and move buttons per row, the first row unable to go up and the last down', async () => {
      const { fixture } = await setupList({ config: makeConfig(undefined, true), store: threeRows() });

      expect(query(fixture, 'embedded-row-drag-handle')).toHaveLength(3);
      expect(query(fixture, 'embedded-row-move-up').map((button) => button.disabled)).toEqual([true, false, false]);
      expect(query(fixture, 'embedded-row-move-down').map((button) => button.disabled)).toEqual([false, false, true]);
    });

    it('has nothing to order with a single row', async () => {
      const { component } = await setupList({ config: makeConfig(undefined, true), store: makeEmbeddedStore([{ id: 'a' }]) });

      expect(component.reorderable()).toBe(false);
    });

    const listed = (component: { rows: () => EmbeddedRow[] }) => component.rows().map((row) => row['id']);

    it('rearranges the rows with the move buttons and leaves saving them to the owner', async () => {
      const store = threeRows();
      const { component, fixture } = await setupList({ config: makeConfig(undefined, true), store });

      query(fixture, 'embedded-row-move-down')[0].click();
      fixture.detectChanges();
      query(fixture, 'embedded-row-move-up')[2].click();
      fixture.detectChanges();

      expect(listed(component)).toEqual(['b', 'c', 'a']);
      expect(component.formGroup.get('embeddedComponents')?.value.map((row: EmbeddedRow) => row['id'])).toEqual(['b', 'c', 'a']);
      expect(component.formGroup.dirty).toBe(true);
    });

    it('moves the dragged row to where it was dropped, and ignores a drop in place', async () => {
      const { component, fixture } = await setupList({ config: makeConfig(undefined, true), store: threeRows() });

      component.dropComponent({ previousIndex: 1, currentIndex: 1 } as never);
      expect(component.formGroup.dirty).toBe(false);

      component.dropComponent({ previousIndex: 0, currentIndex: 2 } as never);
      fixture.detectChanges();
      expect(listed(component)).toEqual(['b', 'c', 'a']);
      expect(component.formGroup.dirty).toBe(true);
    });

    it('keeps the arrangement when a child is added or deleted before the owner is saved', async () => {
      const store = threeRows();
      const { component, fixture } = await setupList({ config: makeConfig(undefined, true), store });
      component.moveComponent({ id: 'c' }, 0);

      store.entities.set([{ id: 'a' }, { id: 'c' }, { id: 'd' }]);
      fixture.detectChanges();

      expect(listed(component)).toEqual(['c', 'a', 'd']);
      expect(component.formGroup.get('embeddedComponents')?.value.map((row: EmbeddedRow) => row['id'])).toEqual(['c', 'a', 'd']);
    });

    it('does not reorder a disabled list', async () => {
      const config = makeConfig(undefined, true);
      config.disabled = true;
      const { component } = await setupList({ config, store: threeRows() });

      component.moveComponent({ id: 'a' }, 1);

      expect(component.reorderable()).toBe(false);
      expect(listed(component)).toEqual(['a', 'b', 'c']);
      expect(component.formGroup.dirty).toBe(false);
    });
  });

  /**
   * A child's save rewrites the containing document, so the owner's form value has to follow — otherwise the
   * owner's own save would merge the array as it looked when the form was built back over the saved one.
   */
  it('mirrors the rows into the attribute’s control', async () => {
    const store = makeEmbeddedStore([{ id: 'a' }]);
    const { component, fixture } = await setupList({ store });

    store.entities.set([{ id: 'a' }, { id: 'b' }]);
    fixture.detectChanges();

    expect(component.formGroup.get('embeddedComponents')?.value).toEqual([{ id: 'a' }, { id: 'b' }]);
    expect(component.formGroup.get('embeddedComponents')?.dirty).toBe(false);
  });
});
