import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslocoService } from '@jsverse/transloco';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DeleteArtifactConfirmationDialog, DeleteArtifactConfirmationDialogData } from './delete-artifact-confirmation.dialog';

describe('DeleteArtifactConfirmationDialog', () => {
  const dialogData: DeleteArtifactConfirmationDialogData = {
    titleKey: 'delete.title',
    contentKey: 'delete.content',
    contentParams: { name: 'Artifact-1' },
    cancelButtonKey: 'delete.cancel',
    confirmButtonKey: 'delete.confirm',
  };
  const translations: Record<string, string> = {
    'delete.title': 'Delete?',
    'delete.content': 'Really delete {{name}}?',
    'delete.cancel': 'Cancel',
    'delete.confirm': 'Delete',
  };
  let dialogRefStub: { close: ReturnType<typeof vi.fn> };
  let translocoStub: { translate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRefStub = { close: vi.fn() };
    translocoStub = {
      translate: vi.fn((key: string, params?: Record<string, string>) => {
        const value = translations[key] ?? key;
        if (!params) return value;
        return Object.entries(params).reduce((acc, [k, v]) => acc.replace(`{{${k}}}`, v), value);
      }),
    };

    await TestBed.configureTestingModule({
      imports: [DeleteArtifactConfirmationDialog, NoopAnimationsModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: dialogData },
        { provide: MatDialogRef, useValue: dialogRefStub },
        { provide: TranslocoService, useValue: translocoStub },
      ],
    }).compileComponents();
  });

  it('renders translated title, content and button labels', () => {
    const fixture = TestBed.createComponent(DeleteArtifactConfirmationDialog);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Delete?');
    expect(text).toContain('Really delete Artifact-1?');
    expect(text).toContain('Cancel');
    expect(text).toContain('Delete');
    expect(translocoStub.translate).toHaveBeenCalledWith('delete.title', undefined);
    expect(translocoStub.translate).toHaveBeenCalledWith('delete.content', dialogData.contentParams);
    expect(translocoStub.translate).toHaveBeenCalledWith('delete.cancel', undefined);
    expect(translocoStub.translate).toHaveBeenCalledWith('delete.confirm', undefined);
  });

  it('closes with false when the cancel button is clicked', () => {
    const fixture = TestBed.createComponent(DeleteArtifactConfirmationDialog);
    fixture.detectChanges();
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('button');

    (buttons[0] as HTMLButtonElement).click();

    expect(dialogRefStub.close).toHaveBeenCalledWith(false);
  });

  it('closes with true when the confirm button is clicked', () => {
    const fixture = TestBed.createComponent(DeleteArtifactConfirmationDialog);
    fixture.detectChanges();
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('button');

    (buttons[1] as HTMLButtonElement).click();

    expect(dialogRefStub.close).toHaveBeenCalledWith(true);
  });

  it('passes contentParams through to the translate call', () => {
    const fixture = TestBed.createComponent(DeleteArtifactConfirmationDialog);
    fixture.detectChanges();

    expect(translocoStub.translate).toHaveBeenCalledWith('delete.content', { name: 'Artifact-1' });
  });
});
