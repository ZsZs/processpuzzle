import { Component, inject } from '@angular/core';
import { BaseEntityContainerComponent, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { createContentBookDescriptor } from '../domain/content-book.descriptors';
import { ContentBookStore } from '../domain/content-book.store';
import { CONTENT_BOOK_CONTENT_TAB } from './content-book/content-book-content-tab';

/** List and Properties of the content books, plus the book itself behind a Content tab — the same two lines as BaseDocumentContainerComponent. */
@Component({
  selector: 'pp-content-book-container',
  imports: [BaseEntityContainerComponent],
  template: ` <base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container> `,
})
export class ContentBookContainerComponent {
  protected readonly entityDescriptor: BaseEntityDescriptor;

  private readonly store = inject(ContentBookStore);

  constructor() {
    this.entityDescriptor = createContentBookDescriptor();
    this.entityDescriptor.store = this.store;
    this.entityDescriptor.extraTabs = [CONTENT_BOOK_CONTENT_TAB];
  }
}
