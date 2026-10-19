import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { IssuedDocument } from './issued-document';
import { IssuedDocumentService } from './issued-document.service';

export const IssuedDocumentStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<IssuedDocument>(IssuedDocument, () => inject(IssuedDocumentService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('IssuedDocument'),
);
