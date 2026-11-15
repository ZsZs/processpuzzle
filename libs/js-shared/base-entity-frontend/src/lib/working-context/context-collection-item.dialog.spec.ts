import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NgxLoggerLevel, provideLogger } from 'ngx-logging-kit';
import { provideRouter } from '@angular/router';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { ContextCollectionItemDialog, ContextCollectionItemDialogData } from './context-collection-item.dialog';

const LOGGING_CONFIGURATION = { level: NgxLoggerLevel.OFF };

function setup(data: Partial<ContextCollectionItemDialogData>) {
  const name = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  name.required = true;
  const descriptor = new BaseEntityDescriptor({ entityName: 'Round', attrDescriptors: [name, new BaseEntityAttrDescriptor('note', FormControlType.TEXT_BOX, 'Note')] });
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    providers: [
      provideLogger(LOGGING_CONFIGURATION),
      provideRouter([]),
      provideTranslocoTesting({ translations: {} }),
      { provide: MAT_DIALOG_DATA, useValue: { descriptor, ...data } },
      { provide: MatDialogRef, useValue: dialogRef },
    ],
  });
  const fixture = TestBed.createComponent(ContextCollectionItemDialog);
  fixture.detectChanges();
  return { fixture, dialogRef, host: fixture.nativeElement as HTMLElement };
}

const button = (host: HTMLElement, testId: string) => host.querySelector(`[data-testid="${testId}"]`) as HTMLButtonElement;

describe('ContextCollectionItemDialog', () => {
  it('builds a control for every attribute of the item type, filled with the item being edited', async () => {
    const { fixture, host } = setup({ item: { id: 'a', name: 'first', note: 'n' } });
    await fixture.whenStable();
    fixture.detectChanges();

    const inputs = host.querySelectorAll('input');
    expect(inputs).toHaveLength(2);
    expect((inputs[0] as HTMLInputElement).value).toBe('first');
  });

  it('closes with the item as edited, keeping the members the form does not show — the id above all', () => {
    const { fixture, dialogRef, host } = setup({ item: { id: 'a', name: 'first', hidden: 1 } });
    const input = host.querySelector('input') as HTMLInputElement;
    input.value = 'renamed';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    button(host, 'context-collection-item-save').click();

    expect(dialogRef.close).toHaveBeenCalledWith(expect.objectContaining({ id: 'a', hidden: 1, name: 'renamed' }));
  });

  it('does not close on a form that is invalid, a required value being missing', () => {
    const { dialogRef, host } = setup({});

    button(host, 'context-collection-item-save').click();

    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('closes with nothing when cancelled', () => {
    const { dialogRef, host } = setup({});

    button(host, 'context-collection-item-cancel').click();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
