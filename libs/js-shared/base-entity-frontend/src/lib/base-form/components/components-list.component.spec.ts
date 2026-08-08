import { ANIMATION_MODULE_TYPE, InjectionToken, Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mock, type MockProxy } from 'vitest-mock-extended';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../../base-entity-facade/base-entity-facade-registry';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';
import { NavigatorCommand } from '../../base-form-navigator/navigation-payload';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { ComponentsListComponent } from './components-list.component';

const PARENT_ENTITY_NAME = 'TestEntity';
const COMPONENT_ENTITY_NAME = 'TestEntityComponent';

interface StoredComponent {
  id: string;
  name: string;
  testEntityId?: string;
}

function makeConfig(): BaseEntityAttrDescriptor {
  const config = new BaseEntityAttrDescriptor('components', FormControlType.COMPONENTS, 'Components');
  config.linkedEntityType = COMPONENT_ENTITY_NAME;
  return config;
}

function makeComponentDescriptor({ componentParent = PARENT_ENTITY_NAME, isEmbedded = false }: { componentParent?: string; isEmbedded?: boolean } = {}): BaseEntityDescriptor {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, undefined, undefined, true);
  const parentAttr = new BaseEntityAttrDescriptor('testEntityId', FormControlType.FOREIGN_KEY);
  parentAttr.linkedEntityType = componentParent;
  return new BaseEntityDescriptor({
    attrDescriptors: isEmbedded ? [nameAttr] : [nameAttr, parentAttr],
    entityName: COMPONENT_ENTITY_NAME,
    entityTitle: COMPONENT_ENTITY_NAME,
    componentParent,
    isEmbedded,
  });
}

function makeComponentStore(entities: StoredComponent[] = []) {
  const state = [...entities];
  return {
    entities: vi.fn(() => state),
    load: vi.fn(),
    loadById: vi.fn((id: string) => state.find((componentEntity) => componentEntity.id === id)),
    delete: vi.fn(() => Promise.resolve()),
    update: vi.fn(() => Promise.resolve(undefined)),
  };
}

function provideComponentFacade(descriptor: BaseEntityDescriptor | undefined, store: unknown): Provider[] {
  if (!descriptor) return [{ provide: BASE_ENTITY_FACADE_REGISTRY, useValue: {} }];

  const facadeToken = new InjectionToken<unknown>('COMPONENT_FACADE');
  return [
    { provide: facadeToken, useValue: { descriptor, store } },
    { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { [COMPONENT_ENTITY_NAME]: facadeToken } },
  ];
}

async function setupList({
  config = makeConfig(),
  references = [] as unknown,
  descriptor = makeComponentDescriptor(),
  registered = true,
  store = makeComponentStore(),
  dialog,
}: {
  config?: BaseEntityAttrDescriptor;
  references?: unknown;
  descriptor?: BaseEntityDescriptor;
  /** False leaves the component entity out of the facade registry. */
  registered?: boolean;
  store?: ReturnType<typeof makeComponentStore>;
  dialog?: MockProxy<MatDialog>;
} = {}) {
  const entity = new TestEntity('parent-1', 'Parent');
  Reflect.set(entity, 'components', references);
  const providers: Provider[] = [...provideComponentFacade(registered ? descriptor : undefined, store)];
  if (dialog) providers.push({ provide: MatDialog, useValue: dialog });

  const harness = await setupFormControlTest(ComponentsListComponent, config, entity, providers);
  return { ...harness, entity, store, component: harness.component as ComponentsListComponent<TestEntity> };
}

describe('ComponentsListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ providers: [{ provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' }] }).compileComponents();
  });

  describe('containment contract', () => {
    it('throws when the component entity is not registered', async () => {
      await expect(setupList({ registered: false })).rejects.toThrow(/not registered in BASE_ENTITY_FACADE_REGISTRY/);
    });

    it('throws when the component does not name this entity as its componentParent', async () => {
      await expect(setupList({ descriptor: makeComponentDescriptor({ componentParent: 'OtherEntity' }) })).rejects.toThrow(/does not name 'TestEntity' as its componentParent/);
    });

    it('throws when the component is embedded, because that is a different control type', async () => {
      await expect(setupList({ descriptor: makeComponentDescriptor({ isEmbedded: true }) })).rejects.toThrow(/EMBEDDED_COMPONENTS/);
    });
  });

  describe('componentEntities()', () => {
    it('normalises stored ids into { id } references', async () => {
      const { component } = await setupList({ references: ['1', '2'] });

      expect(component.componentEntities()).toEqual([{ id: '1' }, { id: '2' }]);
    });

    it('keeps whole objects that a snapshot or seed may still carry', async () => {
      const { component } = await setupList({ references: [{ id: '1', name: 'component_1' }] });

      expect(component.componentEntities()).toEqual([{ id: '1', name: 'component_1' }]);
    });

    it('returns an empty array when the value is not an array', async () => {
      const { component } = await setupList({ references: undefined });

      expect(component.componentEntities()).toEqual([]);
    });
  });

  describe('displayName()', () => {
    it('resolves the identifying attribute from the component store', async () => {
      const store = makeComponentStore([{ id: '1', name: 'component_1' }]);
      const { component } = await setupList({ references: ['1'], store });

      expect(component.displayName({ id: '1' })).toBe('component_1');
    });

    it('prefers the attribute the reference itself carries', async () => {
      const store = makeComponentStore([{ id: '1', name: 'from store' }]);
      const { component } = await setupList({ references: [{ id: '1', name: 'from reference' }], store });

      expect(component.displayName({ id: '1', name: 'from reference' } as never)).toBe('from reference');
    });

    it('falls back to the id while the component store has not loaded it', async () => {
      const { component } = await setupList({ references: ['7'] });

      expect(component.displayName({ id: '7' })).toBe('7');
    });
  });

  describe('loadComponentEntities (ngOnInit)', () => {
    it('loads the component store when it holds nothing, so ids can render as names', async () => {
      const store = makeComponentStore();
      await setupList({ references: ['1'], store });

      expect(store.load).toHaveBeenCalledWith({});
    });

    it('leaves an already loaded store alone', async () => {
      const store = makeComponentStore([{ id: '1', name: 'component_1' }]);
      await setupList({ references: ['1'], store });

      expect(store.load).not.toHaveBeenCalled();
    });
  });

  describe('navigateToComponentList()', () => {
    it('captures the form snapshot and routes the navigator with SELECT_OR_CREATE', async () => {
      const { component } = await setupList({ references: ['1'] });
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      vi.spyOn(formNavigator, 'determineCurrentUrl').mockReturnValue('/parents/parent-1/details');
      vi.spyOn(formNavigator, 'navigateToRelatedList').mockResolvedValue(undefined);
      const captureSpy = vi.spyOn(formNavigator, 'captureFormSnapshot');

      component.navigateToComponentList();

      expect(captureSpy).toHaveBeenCalled();
      expect(formNavigator.navigateToRelatedList).toHaveBeenCalledWith(COMPONENT_ENTITY_NAME, '/parents/parent-1/details', {
        command: NavigatorCommand.SELECT_OR_CREATE,
        attrName: 'components',
        context: [{ id: '1' }],
      });
    });

    it('does nothing when the descriptor is disabled', async () => {
      const config = makeConfig();
      config.disabled = true;
      const { component } = await setupList({ config });
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      const navigateSpy = vi.spyOn(formNavigator, 'navigateToRelatedList').mockResolvedValue(undefined);

      component.navigateToComponentList();

      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });

  describe('attachSelectedComponentFromNavigatorResponse (ngOnInit)', () => {
    it('appends the chosen component as an id and stamps the parent id into its foreign key', async () => {
      const store = makeComponentStore([
        { id: '1', name: 'component_1', testEntityId: 'parent-1' },
        { id: '2', name: 'component_2', testEntityId: '' },
      ]);
      const { component, entity } = await setupList({ references: ['1'], store });
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      formNavigator.pushResponsePayload({
        command: NavigatorCommand.SELECT_OR_CREATE,
        attrName: 'components',
        payload: { id: '2' },
        context: [{ id: '1' }],
      });

      component.ngOnInit();

      expect(component.formGroup.get('components')?.value).toEqual(['1', '2']);
      expect(Reflect.get(entity, 'components')).toEqual(['1', '2']);
      expect(store.update).toHaveBeenCalledWith({ id: '2', name: 'component_2', testEntityId: 'parent-1' });
      expect(component.formGroup.dirty).toBe(true);
    });

    it('leaves the foreign key alone when it already points at this entity', async () => {
      const store = makeComponentStore([{ id: '2', name: 'component_2', testEntityId: 'parent-1' }]);
      const { component } = await setupList({ references: [], store });
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      formNavigator.pushResponsePayload({ command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'components', payload: { id: '2' } });

      component.ngOnInit();

      expect(component.formGroup.get('components')?.value).toEqual(['2']);
      expect(store.update).not.toHaveBeenCalled();
    });

    it('ignores responses that carry no payload', async () => {
      const { component } = await setupList({ references: ['1'] });
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      formNavigator.pushResponsePayload({ command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'components' });

      component.ngOnInit();

      expect(component.formGroup.get('components')?.value).toEqual(['1']);
    });
  });

  describe('deleteComponent()', () => {
    it('destroys the component and drops the reference once the user confirms', async () => {
      const dialog = mock<MatDialog>();
      dialog.open.mockReturnValue({ afterClosed: () => of(true) } as never);
      const store = makeComponentStore([{ id: '1', name: 'component_1' }]);
      const { component, entity } = await setupList({ references: ['1', '2'], store, dialog });

      component.deleteComponent({ id: '1' });

      expect(store.delete).toHaveBeenCalledWith('1');
      expect(component.formGroup.get('components')?.value).toEqual(['2']);
      expect(Reflect.get(entity, 'components')).toEqual(['2']);
    });

    it('keeps everything when the user cancels', async () => {
      const dialog = mock<MatDialog>();
      dialog.open.mockReturnValue({ afterClosed: () => of(false) } as never);
      const store = makeComponentStore([{ id: '1', name: 'component_1' }]);
      const { component } = await setupList({ references: ['1'], store, dialog });

      component.deleteComponent({ id: '1' });

      expect(store.delete).not.toHaveBeenCalled();
      expect(component.formGroup.get('components')?.value).toEqual(['1']);
    });

    it('does nothing when the descriptor is disabled', async () => {
      const dialog = mock<MatDialog>();
      const config = makeConfig();
      config.disabled = true;
      const { component } = await setupList({ config, references: ['1'], dialog });

      component.deleteComponent({ id: '1' });

      expect(dialog.open).not.toHaveBeenCalled();
    });
  });

  describe('template', () => {
    it('renders a row per component and an add button when enabled', async () => {
      const store = makeComponentStore([
        { id: '1', name: 'component_1' },
        { id: '2', name: 'component_2' },
      ]);
      const { fixture } = await setupList({ references: ['1', '2'], store });
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelectorAll('app-component-ref')).toHaveLength(2);
      expect(host.querySelector('button[mat-button]')?.textContent).toContain('Add TestEntityComponent');
    });

    it('hides the add button when the descriptor is disabled', async () => {
      const config = makeConfig();
      config.disabled = true;
      const { fixture } = await setupList({ config, references: ['1'] });

      expect((fixture.nativeElement as HTMLElement).querySelector('button[mat-button]')).toBeNull();
    });

    it('renders nothing when the descriptor is not visible', async () => {
      const config = makeConfig();
      config.visible = false;
      const { fixture } = await setupList({ config, references: ['1'] });

      expect((fixture.nativeElement as HTMLElement).querySelector('fieldset')).toBeNull();
    });
  });
});
