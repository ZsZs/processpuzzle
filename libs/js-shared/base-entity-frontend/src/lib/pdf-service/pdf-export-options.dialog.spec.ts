import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PdfExportOptionsDialog } from './pdf-export-options.dialog';

describe('PdfExportOptionsDialog', () => {
  let dialogRefStub: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRefStub = { close: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [PdfExportOptionsDialog, NoopAnimationsModule],
      providers: [provideTranslocoTesting({ translations: {} }), { provide: MatDialogRef, useValue: dialogRefStub }],
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(PdfExportOptionsDialog);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance as unknown as PdfExportOptionsDialogInternals };
  }

  it('renders the dialog with export and cancel actions', () => {
    const { fixture } = createComponent();
    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('button');
    expect(buttons.length).toBe(2);
  });

  it('defaults to portrait / a4 / footer-on', () => {
    const { component } = createComponent();

    expect(component.orientation()).toBe('portrait');
    expect(component.pageSize()).toBe('a4');
    expect(component.includeFooter()).toBe(true);
  });

  it('closes with the default selection on export', () => {
    const { component } = createComponent();

    component.onExport();

    expect(dialogRefStub.close).toHaveBeenCalledWith({ orientation: 'portrait', pageSize: 'a4', includeFooter: true });
  });

  it('closes with the chosen selection on export', () => {
    const { component } = createComponent();
    component.orientation.set('landscape');
    component.pageSize.set('a3');
    component.includeFooter.set(false);

    component.onExport();

    expect(dialogRefStub.close).toHaveBeenCalledWith({ orientation: 'landscape', pageSize: 'a3', includeFooter: false });
  });

  it('closes with undefined when the cancel button is clicked', () => {
    const { fixture } = createComponent();
    const cancelButton = (fixture.nativeElement as HTMLElement).querySelectorAll('button')[0] as HTMLButtonElement;

    cancelButton.click();

    expect(dialogRefStub.close).toHaveBeenCalledWith(undefined);
  });
});

// The dialog keeps orientation/pageSize/includeFooter and onExport() protected; the tests
// reach them through this structural view rather than widening the component's API.
interface PdfExportOptionsDialogInternals {
  orientation: { (): 'portrait' | 'landscape'; set(value: 'portrait' | 'landscape'): void };
  pageSize: { (): 'a4' | 'a3' | 'letter'; set(value: 'a4' | 'a3' | 'letter'): void };
  includeFooter: { (): boolean; set(value: boolean): void };
  onExport(): void;
}
