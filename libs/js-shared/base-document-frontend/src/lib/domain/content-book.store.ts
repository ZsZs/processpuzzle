import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { ContentBook } from './content-book';
import { ContentBookService } from './content-book.service';

export const ContentBookStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ContentBook>(ContentBook, () => inject(ContentBookService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('ContentBook'),
);
