import { InjectionToken, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from '../../base-entity-facade/base-entity-facade-registry';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { LookupTable } from './lookup-table';
import { LookupComponent } from './lookup.component';

describe('LookupComponent', () => {
  const lookupItems: LookupTable[] = [
    { id: '1', key: 'not-started', value: 'Not Started' },
    { id: '2', key: 'in-progress', value: 'In Progress', description: 'Work is active' },
    { id: '3', key: 'on-hold', value: 'On Hold' },
  ];

  function createLookupConfig(): BaseEntityAttrDescriptor {
    const config = new BaseEntityAttrDescriptor('selectable', FormControlType.LOOKUP, 'Project Status');
    config.linkedEntityType = new BaseEntityDescriptor({
      attrDescriptors: [],
      entityName: 'ProjectStatus',
    });
    return config;
  }

  function createLookupStore() {
    return {
      entities: signal(lookupItems),
      load: vi.fn(),
      loadById: vi.fn((id: string) => lookupItems.find((lookupItem) => lookupItem.id === id || lookupItem.key === id)),
    };
  }

  it('loads the lookup table and displays the value for the current key.', async () => {
    const config = createLookupConfig();
    const entity = new TestEntity('source-id', 'Source entity');
    Reflect.set(entity, 'selectable', 'in-progress');
    const lookupStoreToken = new InjectionToken<ReturnType<typeof createLookupStore>>('LOOKUP_STORE');
    const lookupStore = createLookupStore();

    const { fixture } = await setupFormControlTest(LookupComponent, config, entity, [
      { provide: lookupStoreToken, useValue: lookupStore },
      {
        provide: BASE_ENTITY_FACADE_REGISTRY,
        useValue: {
          ProjectStatus: new BaseEntityDescriptor({
            entityName: 'ProjectStatus',
            attrDescriptors: [],
            store: lookupStoreToken,
          }),
        },
      },
    ]);

    expect(lookupStore.load).toHaveBeenCalledWith({});
    expect((fixture.debugElement.query(By.css('input[matInput]')).nativeElement as HTMLInputElement).value).toEqual('In Progress');
  });

  it('filters lookup items by typed text.', async () => {
    const config = createLookupConfig();
    const entity = new TestEntity('source-id', 'Source entity');
    const lookupStoreToken = new InjectionToken<ReturnType<typeof createLookupStore>>('LOOKUP_STORE');
    const lookupStore = createLookupStore();

    const { component } = await setupFormControlTest(LookupComponent, config, entity, [
      { provide: lookupStoreToken, useValue: lookupStore },
      {
        provide: BASE_ENTITY_FACADE_REGISTRY,
        useValue: {
          ProjectStatus: new BaseEntityDescriptor({
            entityName: 'ProjectStatus',
            attrDescriptors: [],
            store: lookupStoreToken,
          }),
        },
      },
    ]);
    const lookupComponent = component as LookupComponent<TestEntity>;

    lookupComponent.displayControl.setValue('hold');

    expect(lookupComponent.filteredLookupItems()).toEqual([{ id: '3', key: 'on-hold', value: 'On Hold' }]);
  });

  it('saves the selected lookup key to the form control and entity.', async () => {
    const config = createLookupConfig();
    const entity = new TestEntity('source-id', 'Source entity');
    const lookupStoreToken = new InjectionToken<ReturnType<typeof createLookupStore>>('LOOKUP_STORE');
    const lookupStore = createLookupStore();

    const { component } = await setupFormControlTest(LookupComponent, config, entity, [
      { provide: lookupStoreToken, useValue: lookupStore },
      {
        provide: BASE_ENTITY_FACADE_REGISTRY,
        useValue: {
          ProjectStatus: new BaseEntityDescriptor({
            entityName: 'ProjectStatus',
            attrDescriptors: [],
            store: lookupStoreToken,
          }),
        },
      },
    ]);
    const lookupComponent = component as LookupComponent<TestEntity>;

    lookupComponent.selectLookupItem({ option: { value: 'on-hold' } } as never);

    expect(lookupComponent.formGroup.get(config.attrName)?.value).toEqual('on-hold');
    expect(Reflect.get(entity, config.attrName)).toEqual('on-hold');
    expect(lookupComponent.displayControl.value).toEqual('On Hold');
  });

  it('navigates to the referenced lookup entity without selection mode.', async () => {
    const config = createLookupConfig();
    const entity = new TestEntity('source-id', 'Source entity');
    Reflect.set(entity, 'selectable', 'in-progress');
    const lookupStoreToken = new InjectionToken<ReturnType<typeof createLookupStore>>('LOOKUP_STORE');
    const lookupStore = createLookupStore();

    const { component } = await setupFormControlTest(LookupComponent, config, entity, [
      { provide: lookupStoreToken, useValue: lookupStore },
      {
        provide: BASE_ENTITY_FACADE_REGISTRY,
        useValue: {
          ProjectStatus: new BaseEntityDescriptor({
            entityName: 'ProjectStatus',
            attrDescriptors: [],
            store: lookupStoreToken,
          }),
        },
      },
    ]);
    const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
    vi.spyOn(formNavigator, 'determineCurrentUrl').mockReturnValue('/test-entity/source-id/details');
    vi.spyOn(formNavigator, 'navigateToRelated').mockResolvedValue(undefined);

    const lookupComponent = component as LookupComponent<TestEntity>;
    lookupComponent.navigateToRelated();

    expect(formNavigator.navigateToRelated).toHaveBeenCalledWith('ProjectStatus', '2', '/test-entity/source-id/details');
  });
});
