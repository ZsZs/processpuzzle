import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AssignAdminDialog, AssignAdminDialogData } from './assign-admin.dialog';

describe('AssignAdminDialog', () => {
  let fixture: ComponentFixture<AssignAdminDialog>;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRef = { close: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [AssignAdminDialog],
      providers: [
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: MAT_DIALOG_DATA, useValue: { orgKey: 'acme' } satisfies AssignAdminDialogData },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AssignAdminDialog);
    fixture.detectChanges();
  });

  it('requires a valid username and email before submitting', () => {
    expect(fixture.componentInstance.form.invalid).toBe(true);
    fixture.componentInstance.form.patchValue({ username: 'ada', email: 'invalid' });
    expect(fixture.componentInstance.form.invalid).toBe(true);
    fixture.componentInstance.form.patchValue({ email: 'ada@example.test' });
    expect(fixture.componentInstance.form.valid).toBe(true);
  });

  it('trims input and omits blank optional names', () => {
    fixture.componentInstance.form.patchValue({ username: ' ada ', email: 'ada@example.test', firstName: ' ', lastName: ' Lovelace ' });
    fixture.componentInstance.onSubmit();

    expect(dialogRef.close).toHaveBeenCalledWith({
      username: 'ada',
      email: 'ada@example.test',
      firstName: undefined,
      lastName: 'Lovelace',
    });
  });

  it('does not close an invalid form and cancels without a result', () => {
    fixture.componentInstance.onSubmit();
    expect(dialogRef.close).not.toHaveBeenCalled();

    fixture.componentInstance.onCancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
