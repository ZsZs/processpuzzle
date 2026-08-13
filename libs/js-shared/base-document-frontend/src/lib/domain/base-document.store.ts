import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { Document } from './base-document';
import { BaseDocumentService } from './base-document.service';

export const BaseDocumentStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<Document>(Document, () => inject(BaseDocumentService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('BaseDocument'),
);
