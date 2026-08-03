import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { AppDefinitionContainerComponent } from './app-definition-container.component';

describe('AppDefinitionContainerComponent', () => {
  async function setup(currentEntity?: Partial<AppDefinition>) {
    const storeStub = {
      currentEntity: signal(currentEntity ? new AppDefinition(currentEntity) : undefined),
      isLoading: signal(false),
      publish: vi.fn().mockResolvedValue(undefined),
    };

    await TestBed.configureTestingModule({
      imports: [AppDefinitionContainerComponent],
      providers: [{ provide: AppDefinitionStore, useValue: storeStub }],
    })
      .overrideComponent(AppDefinitionContainerComponent, { set: { template: '', imports: [] } })
      .compileComponents();

    const fixture = TestBed.createComponent(AppDefinitionContainerComponent);
    return { fixture, component: fixture.componentInstance, storeStub };
  }

  it('hands the app definition descriptor to the generic container', async () => {
    const { component } = await setup();

    expect(component.entityDescriptor.entityName).toBe('App Definition');
  });

  it('binds the store the descriptor loads from', async () => {
    const { component, storeStub } = await setup();

    expect(component.entityDescriptor.store).toBe(storeStub);
  });

  it('contributes the Publish button to the form actions', async () => {
    const { component } = await setup();

    expect(component.entityDescriptor.extraFormActionsTemplate).toBeDefined();
    expect(component.entityDescriptor.extraFormActionsTemplate?.()).toBe(component.publishActionsTpl());
  });

  it('captions the Publish button from the base_app scope', async () => {
    const { component } = await setup();

    expect(component.publishButtonKey).toBe('base_app.publish.button');
    expect(component.publishTooltipKey).toBe('base_app.publish.tooltip');
  });

  it('cannot publish a definition the backend does not know yet', async () => {
    const { component, storeStub } = await setup();

    expect(component.canPublish()).toBe(false);
    await component.onPublish();
    expect(storeStub.publish).not.toHaveBeenCalled();
  });

  it('publishes the definition currently on the form', async () => {
    const { component, storeStub } = await setup({ id: 'demo', name: 'Demo' });

    expect(component.canPublish()).toBe(true);
    await component.onPublish();
    expect(storeStub.publish).toHaveBeenCalledWith('demo');
  });

  it('keeps Publish disabled while a request is in flight', async () => {
    const { component, storeStub } = await setup({ id: 'demo', name: 'Demo' });

    storeStub.isLoading.set(true);

    expect(component.canPublish()).toBe(false);
  });
});
