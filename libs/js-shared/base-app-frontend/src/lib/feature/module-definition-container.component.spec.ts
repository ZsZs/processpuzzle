import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ModuleDefinitionStore } from '../domain/module-definition.store';
import { ModuleDefinitionContainerComponent } from './module-definition-container.component';

describe('ModuleDefinitionContainerComponent', () => {
  async function setup() {
    const storeStub = { currentEntity: signal(undefined), isLoading: signal(false) };

    await TestBed.configureTestingModule({
      imports: [ModuleDefinitionContainerComponent],
      providers: [{ provide: ModuleDefinitionStore, useValue: storeStub }],
    })
      .overrideComponent(ModuleDefinitionContainerComponent, { set: { template: '', imports: [] } })
      .compileComponents();

    const fixture = TestBed.createComponent(ModuleDefinitionContainerComponent);
    return { component: fixture.componentInstance, storeStub };
  }

  it('hands the module definition descriptor to the generic container', async () => {
    const { component } = await setup();

    expect(component.entityDescriptor.entityName).toBe('Module Definition');
  });

  it('binds the store the descriptor loads from', async () => {
    const { component, storeStub } = await setup();

    expect(component.entityDescriptor.store).toBe(storeStub);
  });

  /** No Publish action, unlike the app container: a module has no lifecycle of its own. */
  it('contributes no extra form actions', async () => {
    const { component } = await setup();

    expect(component.entityDescriptor.extraFormActionsTemplate).toBeUndefined();
  });
});
