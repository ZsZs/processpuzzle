import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { ContentBook } from '../domain/content-book';
import { createContentBookDescriptor } from '../domain/content-book.descriptors';
import { ContentBookMapper } from '../domain/content-book.mapper';
import { ContentBookService } from '../domain/content-book.service';
import { ContentBookStore } from '../domain/content-book.store';

/** Resolves `ContentBook` out of `BASE_ENTITY_FACADE_REGISTRY`, with the root store, mapper and service — as {@link DocumentFacade} does. */
@Injectable()
export class ContentBookFacade extends BaseEntityFacade<ContentBook> {
  readonly entityType = ContentBook;

  private readonly mapperRef = inject(ContentBookMapper);
  private readonly serviceRef = inject(ContentBookService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return ContentBookStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createContentBookDescriptor();
  }
}
