import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { WidgetDefinition } from './widget-definition';
import { WidgetDefinitionContainerComponent } from './widget-definition-container.component';
import { WidgetDefinitionStore } from './widget-definition.store';

describe('WidgetDefinitionContainerComponent', () => {
  async function setup(currentEntity?: Partial<WidgetDefinition>) {
    const storeStub = {
      currentEntity: signal(currentEntity ? new WidgetDefinition(currentEntity) : undefined),
      isLoading: signal(false),
      publish: vi.fn().mockResolvedValue(undefined),
    };

    await TestBed.configureTestingModule({
      imports: [WidgetDefinitionContainerComponent],
      providers: [{ provide: WidgetDefinitionStore, useValue: storeStub }],
    })
      .overrideComponent(WidgetDefinitionContainerComponent, { set: { template: '', imports: [] } })
      .compileComponents();

    const fixture = TestBed.createComponent(WidgetDefinitionContainerComponent);
    return { fixture, component: fixture.componentInstance, storeStub };
  }

  it('hands the widget definition descriptor to the generic container', async () => {
    const { component } = await setup();

    expect(component.entityDescriptor.entityName).toBe('Widget Definition');
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

  it('captions the Publish button from the base_widget scope', async () => {
    const { component } = await setup();

    expect(component.publishButtonKey).toBe('base_widget.publish.button');
    expect(component.publishTooltipKey).toBe('base_widget.publish.tooltip');
  });

  it('cannot publish a definition the backend does not know yet', async () => {
    const { component, storeStub } = await setup();

    expect(component.canPublish()).toBe(false);
    await component.onPublish();
    expect(storeStub.publish).not.toHaveBeenCalled();
  });

  it('publishes the definition currently on the form', async () => {
    const { component, storeStub } = await setup({ id: 'cards-grid', name: 'Cards grid' });

    expect(component.canPublish()).toBe(true);
    await component.onPublish();
    expect(storeStub.publish).toHaveBeenCalledWith('cards-grid');
  });

  it('keeps Publish disabled while a request is in flight', async () => {
    const { component, storeStub } = await setup({ id: 'cards-grid', name: 'Cards grid' });

    storeStub.isLoading.set(true);

    expect(component.canPublish()).toBe(false);
  });
});
