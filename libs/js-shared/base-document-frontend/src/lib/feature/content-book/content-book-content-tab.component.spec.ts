import { Component, input, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ContentBook } from '../../domain/content-book';
import { ContentBookStore } from '../../domain/content-book.store';
import { ContentBookContentTabComponent } from './content-book-content-tab.component';
import { ContentBookWidgetComponent } from './content-book-widget.component';

@Component({ selector: 'pp-content-book', template: '' })
class StubWidget {
  readonly bookSlug = input<string>();
  readonly sidenav = input<boolean>();
}

describe('ContentBookContentTabComponent', () => {
  const currentEntity = signal<ContentBook | undefined>(undefined);
  const setCurrentEntity = vi.fn();

  beforeEach(() => {
    currentEntity.set(undefined);
    setCurrentEntity.mockClear();
    TestBed.configureTestingModule({ imports: [ContentBookContentTabComponent], providers: [{ provide: ContentBookStore, useValue: { currentEntity, setCurrentEntity } }] });
    TestBed.overrideComponent(ContentBookContentTabComponent, { remove: { imports: [ContentBookWidgetComponent] }, add: { imports: [StubWidget] } });
  });

  function render() {
    const fixture = TestBed.createComponent(ContentBookContentTabComponent);
    fixture.componentRef.setInput('entityId', 'b-1');
    fixture.detectChanges();
    return fixture;
  }

  it('makes the routed book current and shows nothing until it is loaded', () => {
    const fixture = render();

    expect(setCurrentEntity).toHaveBeenCalledWith('b-1');
    expect(fixture.debugElement.children.length).toBe(0);
  });

  it('shows the book inline, without a sidenav, once it is loaded', () => {
    const fixture = render();
    currentEntity.set(new ContentBook('b-1', 'org', 'handbook', 'Handbook'));
    fixture.detectChanges();

    const widget = fixture.debugElement.children[0].componentInstance as StubWidget;
    expect(widget.bookSlug()).toBe('handbook');
    expect(widget.sidenav()).toBe(false);
  });
});
