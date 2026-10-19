import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BaseEntityContainerComponent, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { beforeEach, describe, expect, it } from 'vitest';
import { ContentBookStore } from '../domain/content-book.store';
import { ContentBookContainerComponent } from './content-book-container.component';
import { CONTENT_BOOK_CONTENT_TAB } from './content-book/content-book-content-tab';

@Component({ selector: 'base-entity-container', template: '' })
class StubEntityContainer {
  readonly entityDescriptor = input.required<BaseEntityDescriptor>();
}

describe('ContentBookContainerComponent', () => {
  const store = {};

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ContentBookContainerComponent], providers: [{ provide: ContentBookStore, useValue: store }] });
    TestBed.overrideComponent(ContentBookContainerComponent, { remove: { imports: [BaseEntityContainerComponent] }, add: { imports: [StubEntityContainer] } });
  });

  it('hands the entity container the book descriptor, with the root store and the Content tab', () => {
    const fixture = TestBed.createComponent(ContentBookContainerComponent);
    fixture.detectChanges();

    const stub = fixture.debugElement.children[0].componentInstance as StubEntityContainer;
    const descriptor = stub.entityDescriptor();
    expect(descriptor.store).toBe(store);
    expect(descriptor.extraTabs).toEqual([CONTENT_BOOK_CONTENT_TAB]);
  });
});
