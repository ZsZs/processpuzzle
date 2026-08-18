import { Component, input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WIDGET_REGISTRY } from '@processpuzzle/base-widget';
import { describe, expect, it, vi } from 'vitest';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { AppPreviewComponent } from './app-preview.component';

@Component({ selector: 'pp-preview-test-widget', template: `<span class="test-widget">{{ label() }}</span>` })
class PreviewTestWidgetComponent {
  readonly label = input('');
}

describe('AppPreviewTabComponent', () => {
  let fixture: ComponentFixture<AppPreviewComponent>;
  const storeStub = {
    currentEntity: signal<AppDefinition | undefined>(undefined),
    setCurrentEntity: vi.fn(),
  };

  async function render(appId = 'demo-app') {
    await TestBed.configureTestingModule({
      imports: [AppPreviewComponent],
      providers: [
        { provide: AppDefinitionStore, useValue: storeStub },
        { provide: WIDGET_REGISTRY, useValue: new Map([['test-widget', PreviewTestWidgetComponent]]) },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppPreviewComponent);
    fixture.componentRef.setInput('entityId', appId);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('selects the entity on init so the tab bar details and statusbar stay in sync', async () => {
    storeStub.currentEntity.set(new AppDefinition({ id: 'claims-app', name: 'Demo Application', regions: [{ type: 'header' }] }));
    await render('claims-app');

    expect(storeStub.setCurrentEntity).toHaveBeenCalledWith('claims-app');
    expect(fixture.nativeElement.querySelector('.pp-app-preview__title')?.textContent?.trim()).toBe('Demo Application');
  });

  it('renders configured header and footer widgets in declaration order', async () => {
    storeStub.currentEntity.set(
      new AppDefinition({
        id: 'demo-app',
        name: 'Demo Application',
        logoUrl: '/demo-logo.svg',
        regions: [
          { type: 'header', widgets: [{ id: 'language', type: 'test-widget', props: { label: 'Language' } }] },
          { type: 'footer', widgets: [{ id: 'version', type: 'test-widget', props: { label: 'Version' } }] },
        ],
      }),
    );

    await render();

    expect(fixture.nativeElement.querySelector('header img')?.getAttribute('src')).toBe('/demo-logo.svg');
    expect(fixture.nativeElement.querySelector('header .test-widget')?.textContent).toContain('Language');
    expect(fixture.nativeElement.querySelector('footer .test-widget')?.textContent).toContain('Version');
  });

  it('does not invent header or footer regions when they are absent', async () => {
    storeStub.currentEntity.set(new AppDefinition({ id: 'demo-app', name: 'Demo Application' }));

    await render();

    expect(fixture.nativeElement.querySelector('header')).toBeNull();
    expect(fixture.nativeElement.querySelector('footer')).toBeNull();
  });

  it('renders empty regions without requiring a widget registry provider', async () => {
    TestBed.resetTestingModule();
    storeStub.currentEntity.set(
      new AppDefinition({ id: 'demo-app', name: 'Demo Application', regions: [{ type: 'header' }, { type: 'footer' }] }),
    );
    await TestBed.configureTestingModule({
      imports: [AppPreviewComponent],
      providers: [{ provide: AppDefinitionStore, useValue: storeStub }],
    }).compileComponents();
    fixture = TestBed.createComponent(AppPreviewComponent);
    fixture.componentRef.setInput('entityId', 'demo-app');

    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.querySelector('header')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('footer')).not.toBeNull();
  });
});
