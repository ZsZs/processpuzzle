import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { AppPreviewComponent } from 'libs/js-shared/base-app-frontend/src/lib/feature/app-preview.component';

describe('AppPreviewTabComponent', () => {
  let fixture: ComponentFixture<AppPreviewComponent>;
  const storeStub = {
    currentEntity: signal<AppDefinition | undefined>(undefined),
    setCurrentEntity: vi.fn((id: string) => {
      storeStub.currentEntity.set(new AppDefinition({ id, name: 'Demo Application' }));
    }),
  };

  async function render(appId = 'demo-app') {
    await TestBed.configureTestingModule({
      imports: [AppPreviewComponent],
      providers: [{ provide: AppDefinitionStore, useValue: storeStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(AppPreviewComponent);
    fixture.componentRef.setInput('entityId', appId);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('selects the entity on init so the tab bar details and statusbar stay in sync', async () => {
    await render('claims-app');

    expect(storeStub.setCurrentEntity).toHaveBeenCalledWith('claims-app');
    expect(fixture.nativeElement.querySelector('.pp-app-preview__title')?.textContent?.trim()).toBe('Demo Application');
  });
});
