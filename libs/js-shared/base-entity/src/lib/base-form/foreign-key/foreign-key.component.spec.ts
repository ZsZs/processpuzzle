import { InjectionToken } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../../base-entity/base-entity.descriptor';
import { BaseFormNavigatorSingletonStore } from '../../base-form-navigator/base-form-navigator.store';
import { NavigatorCommand } from '../../base-form-navigator/navigation-payload';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { ForeignKeyComponent } from './foreign-key.component';
import { BASE_ENTITY_FACADE_REGISTRY } from '../../base-entity-facade/base-entity-facade-registry';

describe('ForeignKeyComponent', () => {
  const testEntity = new TestEntity('1', 'Test entity', 'Description', true, 100);

  function createForeignKeyConfig(): BaseEntityAttrDescriptor {
    const config = new BaseEntityAttrDescriptor('number', FormControlType.FOREIGN_KEY);
    config.linkedEntityType = 'TestEntityComponent';
    return config;
  }

  function provideLinkedFacade(descriptor: BaseEntityDescriptor, store?: unknown) {
    const facadeToken = new InjectionToken<{ descriptor: BaseEntityDescriptor; store?: unknown }>('LINKED_FACADE');
    const facade = { descriptor, store };
    return [
      { provide: facadeToken, useValue: facade },
      { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: { TestEntityComponent: facadeToken } },
    ];
  }

  function linkedComponentDescriptor(): BaseEntityDescriptor {
    return new BaseEntityDescriptor({
      attrDescriptors: [new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, undefined, undefined, true)],
      entityName: 'TestEntityComponent',
      entityTitle: 'Test Entity Component',
    });
  }

  it('shows Select Entity button when enabled and focused.', async () => {
    const config = createForeignKeyConfig();
    const { fixture } = await setupFormControlTest(ForeignKeyComponent, config, testEntity);
    const row = fixture.debugElement.query(By.css('.row'));

    expect(fixture.debugElement.query(By.css('button[title="Select TestEntityComponent"]'))).toBeNull();

    row.triggerEventHandler('focusin', { currentTarget: row.nativeElement });
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.foreign-key-select-row button[title="Select TestEntityComponent"]')).nativeElement).toBeTruthy();
  });

  it('uses the referenced entity name as label.', async () => {
    const config = createForeignKeyConfig();
    const { fixture } = await setupFormControlTest(ForeignKeyComponent, config, testEntity);

    expect(fixture.debugElement.query(By.css('mat-label')).nativeElement.textContent.trim()).toEqual('TestEntityComponent');
  });

  it('does not show Select Entity button when disabled.', async () => {
    const config = createForeignKeyConfig();
    config.disabled = true;
    const { fixture } = await setupFormControlTest(ForeignKeyComponent, config, testEntity);
    const row = fixture.debugElement.query(By.css('.row'));

    row.triggerEventHandler('focusin', { currentTarget: row.nativeElement });
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('button[title="Select TestEntityComponent"]'))).toBeNull();
  });

  it('navigates to the related list in SELECT_OR_CREATE mode and forwards its attrName.', async () => {
    const config = createForeignKeyConfig();
    const { component } = await setupFormControlTest(ForeignKeyComponent, config, testEntity);
    const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);
    vi.spyOn(formNavigator, 'determineCurrentUrl').mockReturnValue('/test-entity/1/details');
    vi.spyOn(formNavigator, 'navigateToRelatedList').mockResolvedValue(undefined);

    const foreignKeyComponent = component as ForeignKeyComponent<TestEntity>;
    foreignKeyComponent.navigateToRelatedList();

    expect(formNavigator.navigateToRelatedList).toHaveBeenCalledWith('TestEntityComponent', '/test-entity/1/details', {
      command: NavigatorCommand.SELECT_OR_CREATE,
      attrName: config.attrName,
    });
  });

  it('stores only the selected entity ID from SELECT_OR_CREATE response payload addressed by attrName.', async () => {
    const config = createForeignKeyConfig();
    const { fixture, component } = await setupFormControlTest(ForeignKeyComponent, config, testEntity, provideLinkedFacade(linkedComponentDescriptor()));
    const formNavigator = TestBed.inject(BaseFormNavigatorSingletonStore);

    formNavigator.pushResponsePayload({ command: NavigatorCommand.SELECT_OR_CREATE, attrName: config.attrName, payload: new TestEntity('selected-id', 'Selected entity') });
    const foreignKeyComponent = component as ForeignKeyComponent<TestEntity>;
    foreignKeyComponent.ngOnInit();
    fixture.detectChanges();

    expect(foreignKeyComponent.formGroup.get(config.attrName)?.value).toEqual('selected-id');
    expect(Reflect.get(testEntity, config.attrName)).toEqual('selected-id');
    expect((fixture.debugElement.query(By.css('mat-form-field input')).nativeElement as HTMLInputElement).value).toEqual('Selected entity');
  });

  it('resolves the referenced entity display name from the facade registry.', async () => {
    const config = createForeignKeyConfig();
    const entity = new TestEntity('source-id', 'Source entity', 'Description', true, 100);
    const linkedEntity = new TestEntity('100', 'Registry entity');
    const linkedStore = {
      loadById: vi.fn().mockReturnValue(linkedEntity),
    };

    const { fixture } = await setupFormControlTest(ForeignKeyComponent, config, entity, provideLinkedFacade(linkedComponentDescriptor(), linkedStore));

    expect((fixture.debugElement.query(By.css('mat-form-field input')).nativeElement as HTMLInputElement).value).toEqual('Registry entity');
    expect(linkedStore.loadById).toHaveBeenCalledWith('100');
  });
});
