import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Document, DocumentKind } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { AddDocumentDialog, AddDocumentDialogData, slugify } from './add-document.dialog';

describe('slugify', () => {
  it('turns a title into a slug the contract accepts', () => {
    expect(slugify('Getting Started — Part 1!')).toBe('getting-started-part-1');
    expect(slugify('Árvíztűrő tükörfúrógép')).toBe('arvizturo-tukorfurogep');
    expect(slugify('  --  ')).toBe('');
  });
});

describe('AddDocumentDialog', () => {
  let fixture: ComponentFixture<AddDocumentDialog>;
  let documentService: { add: ReturnType<typeof vi.fn>; findByQuery: ReturnType<typeof vi.fn> };
  let close: ReturnType<typeof vi.fn>;
  const data: AddDocumentDialogData = { locale: 'hu', isPublic: true, readerRoles: ['reader'], editorRoles: ['editor'], excludeDocumentIds: ['d-in-book'] };

  const byTestId = (id: string): HTMLElement | null => document.querySelector(`[data-testid="${id}"]`);
  const dialog = (): any => fixture.componentInstance;

  beforeEach(async () => {
    close = vi.fn();
    documentService = {
      add: vi.fn((draft: Document) => of({ ...draft, id: 'd-new' })),
      findByQuery: vi.fn(() =>
        of({
          content: [
            new Document('d-in-book', 'org', 'in-book', 'In book'),
            new Document('d-free', 'org', 'free', 'Free page'),
            new Document('d-template', 'org', 'invoice', 'Invoice', undefined, undefined, undefined, 'en', false, [], [], [], [], [], [], undefined, undefined, undefined, undefined, DocumentKind.REPORT_TEMPLATE),
          ],
          number: 0,
          size: 3,
          totalElements: 3,
          totalPages: 1,
        }),
      ),
    };
    TestBed.configureTestingModule({
      imports: [AddDocumentDialog],
      providers: [
        provideTranslocoTesting({ translations: { en: {} } }),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
        { provide: BaseDocumentService, useValue: documentService },
      ],
    });
    fixture = TestBed.createComponent(AddDocumentDialog);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('offers only documents that are neither in the book nor report templates', () => {
    expect(dialog().candidates().map((candidate: Document) => candidate.id)).toEqual(['d-free']);
  });

  it('derives the slug from the title until the slug is typed in itself', () => {
    dialog().title.setValue('My New Page');
    dialog().onTitleTyped();
    expect(dialog().slug.value).toBe('my-new-page');

    dialog().slugTouched = true;
    dialog().title.setValue('Renamed');
    dialog().onTitleTyped();
    expect(dialog().slug.value).toBe('my-new-page');
  });

  it('creates a new document in the book’s language and with its access, and closes with it', async () => {
    dialog().title.setValue('My New Page');
    dialog().onTitleTyped();

    await dialog().submit();

    const draft = documentService.add.mock.calls[0][0] as Document;
    expect(draft).toEqual(expect.objectContaining({ slug: 'my-new-page', title: 'My New Page', sourceLocale: 'hu', isPublic: true, readerRoles: ['reader'], editorRoles: ['editor'] }));
    expect(close).toHaveBeenCalledWith(expect.objectContaining({ id: 'd-new' }));
  });

  it('keeps the dialog open with the server’s reason when the create is refused', async () => {
    documentService.add.mockReturnValue(throwError(() => new Error('slug taken')));
    dialog().title.setValue('Taken');
    dialog().onTitleTyped();

    await dialog().submit();

    expect(close).not.toHaveBeenCalled();
    expect(dialog().error()).toContain('slug taken');
  });

  it('closes with the existing document picked', async () => {
    dialog().mode.set('existing');
    fixture.detectChanges();
    dialog().search.setValue('free');
    dialog().onChosen({ option: { value: 'Free page' } });

    expect(dialog().canSubmit()).toBe(true);
    await dialog().submit();

    expect(close).toHaveBeenCalledWith(expect.objectContaining({ id: 'd-free' }));
    expect(byTestId('add-document-search')).not.toBeNull();
  });

  it('says so when every document is already in the book', () => {
    dialog().candidates.set([]);
    dialog().mode.set('existing');
    fixture.detectChanges();

    expect(byTestId('add-document-none-left')).not.toBeNull();
  });
});
