import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { AppDefinitionContainerComponent } from './app-definition-container.component';
import { APP_PREVIEW_TAB } from './app-preview-tab';

describe('AppDefinitionContainerComponent', () => {
  async function setup(currentEntity?: Partial<AppDefinition>) {
    const storeStub = {
      currentEntity: signal(currentEntity ? new AppDefinition(currentEntity) : undefined),
      isLoading: signal(false),
    };

    await TestBed.configureTestingModule({
      imports: [AppDefinitionContainerComponent],
      providers: [
        provideRouter([]),
        { provide: AppDefinitionStore, useValue: storeStub },
      ],
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

  it('declares the preview tab the routes mount', async () => {
    const { component } = await setup();

    expect(component.entityDescriptor.extraTabs).toEqual([APP_PREVIEW_TAB]);
  });

  it('contributes no Publish action, since publishing is the release of the organization', async () => {
    const { component } = await setup();

    expect(component.entityDescriptor.extraFormActionsTemplate).toBeUndefined();
  });
});
