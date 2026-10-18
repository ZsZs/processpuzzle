import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocomplete, MatAutocompleteSelectedEvent, MatAutocompleteTrigger, MatOption } from '@angular/material/autocomplete';
import { MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { Document, DOCUMENT_SLUG_PATTERN, DocumentKind } from '../../domain/base-document';
import { BaseDocumentService } from '../../domain/base-document.service';
import { contentOf } from '../../domain/content-book.service';

export interface AddDocumentDialogData {
  /** Source locale of a new document — the language the book is read in. */
  locale: string;
  /** Access a new document starts with: the book's own, so a page added to a private book is not public by accident. */
  isPublic: boolean;
  readerRoles: string[];
  editorRoles: string[];
  /** Documents the book already shows; a document is filed in a book at most once. */
  excludeDocumentIds: string[];
}

type Mode = 'new' | 'existing';

/** Lower-case, hyphenated, accents dropped — a slug the contract's pattern accepts, from whatever title was typed. */
export function slugify(title: string): string {
  return title
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .split('-')
    .filter((part) => part.length > 0)
    .join('-')
    .slice(0, 100);
}

/**
 * Picks the document a new page of a content book shows: a new one, created here with the book's locale and
 * access, or one the organization already has. Closes with the {@link Document}; the caller files it.
 */
@Component({
  selector: 'pp-content-book-add-document-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogTitle,
    MatDialogContent,
    MatDialogActions,
    MatDialogClose,
    MatButton,
    MatButtonToggleGroup,
    MatButtonToggle,
    MatFormField,
    MatLabel,
    MatInput,
    MatError,
    MatAutocomplete,
    MatAutocompleteTrigger,
    MatOption,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'base_document.content_book.widget.dialog.title' | transloco }}</h2>
    <mat-dialog-content class="pp-add-document">
      <mat-button-toggle-group [value]="mode()" (change)="mode.set($event.value)" hideSingleSelectionIndicator>
        <mat-button-toggle value="new" data-testid="add-document-mode-new">{{ 'base_document.content_book.widget.dialog.new' | transloco }}</mat-button-toggle>
        <mat-button-toggle value="existing" data-testid="add-document-mode-existing">{{ 'base_document.content_book.widget.dialog.existing' | transloco }}</mat-button-toggle>
      </mat-button-toggle-group>

      @if (mode() === 'new') {
        <mat-form-field>
          <mat-label>{{ 'base_document.content_book.widget.dialog.document_title' | transloco }}</mat-label>
          <input matInput [formControl]="title" data-testid="add-document-title" (input)="onTitleTyped()" />
        </mat-form-field>
        <mat-form-field>
          <mat-label>{{ 'base_document.content_book.widget.dialog.document_slug' | transloco }}</mat-label>
          <input matInput [formControl]="slug" data-testid="add-document-slug" (input)="slugTouched = true" />
          @if (slug.invalid) {
            <mat-error>{{ slugPattern }}</mat-error>
          }
        </mat-form-field>
      } @else if (candidates().length === 0 && loaded()) {
        <p data-testid="add-document-none-left">{{ 'base_document.content_book.widget.dialog.none_left' | transloco }}</p>
      } @else {
        <mat-form-field>
          <mat-label>{{ 'base_document.content_book.widget.dialog.document' | transloco }}</mat-label>
          <input matInput [formControl]="search" [matAutocomplete]="documents" data-testid="add-document-search" />
          <mat-autocomplete #documents (optionSelected)="onChosen($event)">
            @for (document of matches(); track document.id) {
              <mat-option [value]="document.title">{{ document.title }} <small>({{ document.slug }})</small></mat-option>
            }
          </mat-autocomplete>
        </mat-form-field>
      }
      @if (error(); as error) {
        <p role="alert" class="pp-add-document__error">{{ error }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="undefined">{{ 'base_document.content_book.widget.dialog.cancel' | transloco }}</button>
      <button mat-raised-button type="button" data-testid="add-document-confirm" [disabled]="!canSubmit() || busy()" (click)="submit()">
        {{ 'base_document.content_book.widget.dialog.add' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .pp-add-document {
      display: flex;
      flex-direction: column;
      gap: 12px;
      min-width: 360px;
    }
    .pp-add-document__error {
      color: var(--mat-sys-error, #b3261e);
    }
  `,
})
export class AddDocumentDialog implements OnInit {
  protected readonly data = inject<AddDocumentDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<AddDocumentDialog, Document>);
  private readonly documentService = inject(BaseDocumentService);

  protected readonly slugPattern = DOCUMENT_SLUG_PATTERN;
  protected readonly mode = signal<Mode>('new');
  protected readonly title = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly slug = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(DOCUMENT_SLUG_PATTERN)] });
  protected readonly search = new FormControl('', { nonNullable: true });
  protected slugTouched = false;

  protected readonly candidates = signal<Document[]>([]);
  protected readonly loaded = signal(false);
  protected readonly chosen = signal<Document | undefined>(undefined);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | undefined>(undefined);

  private readonly searchText = toSignal(this.search.valueChanges, { initialValue: '' });
  private readonly titleValid = toSignal(this.title.statusChanges, { initialValue: this.title.status });
  private readonly slugValid = toSignal(this.slug.statusChanges, { initialValue: this.slug.status });

  protected readonly matches = computed(() => {
    const text = this.searchText().toLowerCase();
    return this.candidates().filter((document) => document.title.toLowerCase().includes(text) || document.slug.includes(text));
  });
  protected readonly canSubmit = computed(() => (this.mode() === 'new' ? this.titleValid() === 'VALID' && this.slugValid() === 'VALID' : this.chosen() !== undefined));

  ngOnInit(): void {
    void this.loadCandidates();
  }

  protected onTitleTyped(): void {
    if (!this.slugTouched) this.slug.setValue(slugify(this.title.value));
  }

  protected onChosen(event: MatAutocompleteSelectedEvent): void {
    this.chosen.set(this.matches().find((document) => document.title === event.option.value));
  }

  protected async submit(): Promise<void> {
    if (this.mode() === 'existing') {
      this.dialogRef.close(this.chosen());
      return;
    }
    this.busy.set(true);
    this.error.set(undefined);
    try {
      const { locale, isPublic, readerRoles, editorRoles } = this.data;
      const draft = new Document(undefined, undefined, this.slug.value, this.title.value, undefined, undefined, undefined, locale, isPublic, [...readerRoles], [...editorRoles]);
      this.dialogRef.close(await firstValueFrom(this.documentService.add(draft)));
    } catch (error) {
      this.error.set(error instanceof HttpErrorResponse ? (error.error?.errorText ?? error.message) : String(error));
    } finally {
      this.busy.set(false);
    }
  }

  /** Report templates are left out: they render once per subject and have nothing to show as a page. */
  private async loadCandidates(): Promise<void> {
    try {
      const documents = contentOf(await firstValueFrom(this.documentService.findByQuery({ pageSize: 500, orderBys: [{ property: 'title', direction: 'asc' }] })));
      this.candidates.set(documents.filter((document) => document.kind !== DocumentKind.REPORT_TEMPLATE && !this.data.excludeDocumentIds.includes(document.id as string)));
    } catch {
      this.candidates.set([]);
    } finally {
      this.loaded.set(true);
    }
  }
}
