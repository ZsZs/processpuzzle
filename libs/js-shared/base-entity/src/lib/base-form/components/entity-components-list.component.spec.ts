import { InjectionToken, signal, type InputSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../../base-entity-facade/base-entity-facade-registry';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';
import { NavigatorCommand } from '../../base-form-navigator/navigation-payload';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { EntityComponentsListComponent } from './entity-components-list.component';

function makeConfig(): BaseEntityAttrDescriptor {
  const config = new BaseEntityAttrDescriptor('components', FormControlType.COMPONENTS, 'Components');
  config.linkedEntityType = 'LineItem';
  return config;
}

function provideLinkedFacade(linkedEntityName = 'LineItem') {
  const linkAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, undefined, undefined, true);
  const descriptor = new BaseEntityDescriptor({
    attrDescriptors: [linkAttr],
    entityName: linkedEntityName,
    entityTitle: linkedEntityName,
  });
  const facadeToken = new InjectionToken<{ descriptor: BaseEntityDescriptor }>('LINKED_FACADE');
  return [
    { provide: facadeToken, useValue: { descriptor } },
    { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { [linkedEntityName]: facadeToken } },
  ];
}

async function setupList(config: BaseEntityAttrDescriptor, initialComponents: unknown[] | unknown) {
  const entity = new TestEntity('parent-1', 'Parent');
  Reflect.set(entity, 'components', initialComponents);
  const harness = await setupFormControlTest(EntityComponentsListComponent, config, entity, provideLinkedFacade());
  return { ...harness, entity, component: harness.component as EntityComponentsListComponent<TestEntity> };
}

describe('EntityComponentsListComponent', () => {
  describe('components()', () => {
    it('returns the form control array value', async () => {
      const items = [new TestEntity('a', 'A'), new TestEntity('b', 'B')];
      const { component } = await setupList(makeConfig(), items);

      expect(component.components()).toEqual(items);
    });

    it('falls back to the input value when the form control is empty', async () => {
      const items = [new TestEntity('c', 'C')];
      const { component, fixture } = await setupList(makeConfig(), []);
      (component.value as unknown as InputSignal<unknown>) = signal(items) as unknown as InputSignal<unknown>;
      // ensure value() returns items and form control is empty
      component.formGroup.get('components')?.setValue(null);
      fixture.detectChanges();

      expect(component.components()).toEqual(items);
    });

    it('returns an empty array when the value is not an array', async () => {
      const { component } = await setupList(makeConfig(), 'not-an-array');

      expect(component.components()).toEqual([]);
    });

    it('asserts each element is a persisted entity', async () => {
      const { component } = await setupList(makeConfig(), [new TestEntity('a', 'A')]);
      component.formGroup.get('components')?.setValue([{ id: undefined } as unknown as TestEntity]);

      expect(() => component.components()).toThrow('Persisted entity must have an id');
    });

    it('normalises primitive ids into { id } objects', async () => {
      const { component } = await setupList(makeConfig(), []);
      component.formGroup.get('components')?.setValue(['a', 7]);

      expect(component.components()).toEqual([{ id: 'a' }, { id: '7' }]);
    });

    it('promotes the configured referenceIdField to id when items lack one', async () => {
      const config = makeConfig();
      config.referenceIdField = 'code';
      const { component } = await setupList(config, []);
      component.formGroup.get('components')?.setValue([{ code: 'X-1', label: 'first' }]);

      expect(component.components()).toEqual([{ code: 'X-1', label: 'first', id: 'X-1' }]);
    });
  });

  describe('componentNameAttr()', () => {
    it('exposes the link-to-details attribute name from the linked descriptor and the host attribute name', async () => {
      const { component } = await setupList(makeConfig(), []);

      expect(component.componentNameAttr()).toEqual({ attrName: 'name', name: 'components' });
    });

    it('returns an empty attrName when the linked descriptor has no detail-link attribute', async () => {
      const entity = new TestEntity('parent-1', 'Parent');
      Reflect.set(entity, 'components', []);
      const config = makeConfig();
      const noLinkAttr = new BaseEntityAttrDescriptor('label', FormControlType.TEXT_BOX);
      const descriptor = new BaseEntityDescriptor({
        attrDescriptors: [noLinkAttr],
        entityName: 'LineItem',
        entityTitle: 'LineItem',
      });
      const facadeToken = new InjectionToken<{ descriptor: BaseEntityDescriptor }>('LINKED_FACADE');
      const harness = await setupFormControlTest(EntityComponentsListComponent, config, entity, [
        { provide: facadeToken, useValue: { descriptor } },
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { LineItem: facadeToken } },
      ]);

      expect((harness.component as EntityComponentsListComponent<TestEntity>).componentNameAttr()).toEqual({ attrName: '', name: 'components' });
    });
  });

  describe('addComponentTitle()', () => {
    it('returns "Add" prefixed with the linked entity name', async () => {
      const { component } = await setupList(makeConfig(), []);

      expect(component.addComponentTitle()).toBe('Add LineItem');
    });
  });

  describe('navigateToRelatedList()', () => {
    it('captures the form snapshot and routes the navigator with the SELECT_OR_CREATE command, attrName, and context', async () => {
      const items = [new TestEntity('a', 'A')];
      const { component } = await setupList(makeConfig(), items);
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      vi.spyOn(formNavigator, 'determineCurrentUrl').mockReturnValue('/parents/parent-1/details');
      vi.spyOn(formNavigator, 'navigateToRelatedList').mockResolvedValue(undefined);
      const captureSpy = vi.spyOn(formNavigator, 'captureFormSnapshot');

      component.navigateToRelatedList();

      expect(captureSpy).toHaveBeenCalled();
      expect(formNavigator.navigateToRelatedList).toHaveBeenCalledWith('LineItem', '/parents/parent-1/details', {
        command: NavigatorCommand.SELECT_OR_CREATE,
        attrName: 'components',
        context: items,
      });
    });

    it('does nothing when the descriptor is disabled', async () => {
      const config = makeConfig();
      config.disabled = true;
      const { component } = await setupList(config, []);
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      const navigateSpy = vi.spyOn(formNavigator, 'navigateToRelatedList').mockResolvedValue(undefined);

      component.navigateToRelatedList();

      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });

  describe('addSelectedComponentFromNavigatorResponse (ngOnInit)', () => {
    it('appends the selected component from a SELECT_OR_CREATE response to the form control and entity', async () => {
      const initial = new TestEntity('a', 'A');
      const selected = new TestEntity('b', 'B');
      const { component, entity, fixture } = await setupList(makeConfig(), [initial]);

      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      formNavigator.pushResponsePayload({
        command: NavigatorCommand.SELECT_OR_CREATE,
        attrName: 'components',
        payload: selected,
        context: [initial],
      });

      component.ngOnInit();
      fixture.detectChanges();

      expect(component.formGroup.get('components')?.value).toEqual([initial, selected]);
      expect(Reflect.get(entity, 'components')).toEqual([initial, selected]);
      expect(component.formGroup.dirty).toBe(true);
      expect(component.formGroup.touched).toBe(true);
    });

    it('falls back to the current components when the response has no context array', async () => {
      const initial = new TestEntity('a', 'A');
      const selected = new TestEntity('b', 'B');
      const { component } = await setupList(makeConfig(), [initial]);

      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      formNavigator.pushResponsePayload({
        command: NavigatorCommand.SELECT_OR_CREATE,
        attrName: 'components',
        payload: selected,
      });

      component.ngOnInit();

      expect(component.formGroup.get('components')?.value).toEqual([initial, selected]);
    });

    it('does nothing when the descriptor is disabled', async () => {
      const config = makeConfig();
      config.disabled = true;
      const { component } = await setupList(config, []);
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      const popSpy = vi.spyOn(formNavigator, 'popResponsePayload');

      component.ngOnInit();

      expect(popSpy).not.toHaveBeenCalled();
    });

    it('does nothing when the response is missing', async () => {
      const initial = new TestEntity('a', 'A');
      const { component } = await setupList(makeConfig(), [initial]);

      component.ngOnInit();

      expect(component.formGroup.get('components')?.value).toEqual([initial]);
    });

    it('ignores responses that do not carry a SELECT_OR_CREATE payload', async () => {
      const initial = new TestEntity('a', 'A');
      const { component } = await setupList(makeConfig(), [initial]);
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      formNavigator.pushResponsePayload({ command: NavigatorCommand.EDIT, attrName: 'components', payload: new TestEntity('b', 'B') });

      component.ngOnInit();

      expect(component.formGroup.get('components')?.value).toEqual([initial]);
    });

    it('ignores SELECT_OR_CREATE responses without a payload', async () => {
      const initial = new TestEntity('a', 'A');
      const { component } = await setupList(makeConfig(), [initial]);
      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      formNavigator.pushResponsePayload({ command: NavigatorCommand.SELECT_OR_CREATE, attrName: 'components' });

      component.ngOnInit();

      expect(component.formGroup.get('components')?.value).toEqual([initial]);
    });

    it('logs a warning when no matching form control exists', async () => {
      const initial = new TestEntity('a', 'A');
      const selected = new TestEntity('b', 'B');
      const { component, entity } = await setupList(makeConfig(), [initial]);
      component.formGroup.removeControl('components');
      const warnSpy = vi.spyOn(component['logger'], 'warn').mockImplementation(() => undefined);

      const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
      formNavigator.pushResponsePayload({
        command: NavigatorCommand.SELECT_OR_CREATE,
        attrName: 'components',
        payload: selected,
        context: [initial],
      });

      component.ngOnInit();

      expect(warnSpy).toHaveBeenCalled();
      expect(Reflect.get(entity, 'components')).toEqual([initial, selected]);
    });
  });

  describe('template', () => {
    it('renders a chip per component and an add button when enabled', async () => {
      const items = [new TestEntity('a', 'Alpha'), new TestEntity('b', 'Beta')];
      const { fixture } = await setupList(makeConfig(), items);
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelectorAll('app-entity-component-ref')).toHaveLength(2);
      const addButton = host.querySelector('button[mat-button]');
      expect(addButton?.textContent).toContain('Add LineItem');
    });

    it('hides the add button when the descriptor is disabled', async () => {
      const config = makeConfig();
      config.disabled = true;
      const { fixture } = await setupList(config, [new TestEntity('a', 'Alpha')]);
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('button[mat-button]')).toBeNull();
    });

    it('renders nothing when the descriptor is not visible', async () => {
      const config = makeConfig();
      config.visible = false;
      const { fixture } = await setupList(config, [new TestEntity('a', 'Alpha')]);
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('fieldset')).toBeNull();
    });
  });
});
