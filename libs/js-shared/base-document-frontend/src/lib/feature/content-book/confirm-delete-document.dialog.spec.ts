import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmDeleteDocumentDialog } from './confirm-delete-document.dialog';

describe('ConfirmDeleteDocumentDialog', () => {
  const close = vi.fn();

  beforeEach(() => {
    close.mockClear();
    TestBed.configureTestingModule({
      imports: [ConfirmDeleteDocumentDialog],
      providers: [
        provideTranslocoTesting({ translations: { en: {} } }),
        { provide: MAT_DIALOG_DATA, useValue: { title: 'Intro' } },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
  });

  const click = (testId: string) => {
    const fixture = TestBed.createComponent(ConfirmDeleteDocumentDialog);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)?.click();
  };

  it('closes with true when confirmed', () => {
    click('content-book-delete-confirm');
    expect(close).toHaveBeenCalledWith(true);
  });

  it('closes with false when cancelled', () => {
    click('content-book-delete-cancel');
    expect(close).toHaveBeenCalledWith(false);
  });
});
