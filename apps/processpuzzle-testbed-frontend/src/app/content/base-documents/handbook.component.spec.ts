import { describe, expect, it } from 'vitest';
import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ContentBookWidgetComponent } from '@processpuzzle/base-document';
import { HandbookComponent } from './handbook.component';

@Component({ selector: 'pp-content-book', template: '' })
class StubContentBookComponent {
  readonly bookSlug = input<string>();
}

describe('HandbookComponent', () => {
  it('points the content-book widget at the seeded handbook', () => {
    TestBed.configureTestingModule({ imports: [HandbookComponent] }).overrideComponent(HandbookComponent, {
      remove: { imports: [ContentBookWidgetComponent] },
      add: { imports: [StubContentBookComponent] },
    });

    const fixture = TestBed.createComponent(HandbookComponent);
    fixture.detectChanges();

    const widget = fixture.debugElement.children[0];
    expect(widget.name).toBe('pp-content-book');
    expect(widget.componentInstance.bookSlug()).toBe('testbed-handbook');
  });
});
