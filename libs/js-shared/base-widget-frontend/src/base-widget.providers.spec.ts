import { Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import {
  CARDS_GRID_WIDGET,
  LANGUAGE_SELECTOR_WIDGET,
  LIKE_BUTTON_WIDGET,
  MARKDOWN_PAGE_WIDGET,
  SHARE_BUTTON_WIDGET,
  VERSION_BUTTON_WIDGET,
  provideBaseWidgets,
  provideCardsGridWidget,
} from './base-widget.providers';
import { MatCardsGridComponent } from './mat-cards-grid/mat-cards-grid.component';
import { WIDGET_REGISTRY, provideWidget } from './widget-registry/widget-registry.token';

describe('base-widget providers', () => {
  it('registers every shipped widget under its documented key', () => {
    TestBed.configureTestingModule({ providers: [provideBaseWidgets()] });

    const registry = TestBed.inject(WIDGET_REGISTRY);

    expect([...registry.keys()].sort()).toEqual([CARDS_GRID_WIDGET, LANGUAGE_SELECTOR_WIDGET, LIKE_BUTTON_WIDGET, MARKDOWN_PAGE_WIDGET, SHARE_BUTTON_WIDGET, VERSION_BUTTON_WIDGET].sort());
    expect(registry.get(CARDS_GRID_WIDGET)).toBe(MatCardsGridComponent);
  });

  it('registers a single widget without the others', () => {
    TestBed.configureTestingModule({ providers: [provideCardsGridWidget()] });

    const registry = TestBed.inject(WIDGET_REGISTRY);

    expect([...registry.keys()]).toEqual([CARDS_GRID_WIDGET]);
  });

  // The property an aggregator relies on: base-document registering `document-viewer` from its own
  // lib must add to this library's widgets rather than replace them.
  it('merges with a widget registered by another lib', () => {
    class DocumentViewerComponent {}
    TestBed.configureTestingModule({ providers: [provideBaseWidgets()] });
    const parent = TestBed.inject(Injector);

    const child = Injector.create({ providers: [provideWidget('document-viewer', DocumentViewerComponent)], parent });
    const registry = runInInjectionContext(child, () => child.get(WIDGET_REGISTRY));

    expect(registry.get('document-viewer')).toBe(DocumentViewerComponent);
    expect(registry.get(CARDS_GRID_WIDGET)).toBe(MatCardsGridComponent);
  });
});
