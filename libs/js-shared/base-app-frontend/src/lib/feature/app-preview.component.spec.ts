import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { AppPreviewComponent } from './app-preview.component';

/**
 * What the *container* owes: selecting the previewed definition and handing it to the shell. How an
 * application renders from that definition is `app-shell.component.spec.ts`' subject, and asserting it
 * twice would only make the shell harder to change.
 */
describe('AppPreviewComponent', () => {
  let fixture: ComponentFixture<AppPreviewComponent>;
  const storeStub = {
    currentEntity: signal<AppDefinition | undefined>(undefined),
    setCurrentEntity: vi.fn(),
  };

  async function render(appId = 'demo-app') {
    await TestBed.configureTestingModule({
      imports: [AppPreviewComponent],
      providers: [provideRouter([]), { provide: AppDefinitionStore, useValue: storeStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(AppPreviewComponent);
    fixture.componentRef.setInput('entityId', appId);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('selects the entity on init so the tab bar details and statusbar stay in sync', async () => {
    storeStub.currentEntity.set(new AppDefinition({ id: 'claims-app', name: 'Demo Application' }));

    await render('claims-app');

    expect(storeStub.setCurrentEntity).toHaveBeenCalledWith('claims-app');
  });

  it('hands the selected definition to the shell', async () => {
    storeStub.currentEntity.set(new AppDefinition({ id: 'demo-app', name: 'Demo Application', regions: [{ type: 'footer' }] }));

    await render();

    // The shell is what renders the region, so its presence is what proves the definition arrived.
    expect(fixture.nativeElement.querySelector('pp-app-shell pp-region-footer')).not.toBeNull();
  });

  it('frames a shell even before a definition has resolved', async () => {
    storeStub.currentEntity.set(undefined);

    await render();

    expect(fixture.nativeElement.querySelector('.pp-app-preview pp-app-shell')).not.toBeNull();
  });
});
