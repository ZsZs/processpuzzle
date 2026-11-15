import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { WorkingContextPickerComponent } from './working-context-picker.component';

export interface WorkingContextPickerDialogData {
  contextName: string;
  /** The translated entity name, for the title. */
  entityLabel: string;
}

/** Dialog configuration that turns the picker into a full-height drawer on the right edge. */
export const WORKING_CONTEXT_DRAWER_CONFIG = {
  position: { right: '0', top: '0' },
  height: '100%',
  maxHeight: '100vh',
  width: '420px',
  maxWidth: '100vw',
  panelClass: 'pp-working-context-drawer',
} as const;

/** The picker in a drawer, which the status bar's context crumb opens to switch. */
@Component({
  selector: 'pp-working-context-picker-dialog',
  standalone: true,
  imports: [MatDialogTitle, MatDialogContent, TranslocoPipe, WorkingContextPickerComponent],
  template: `
    <h2 mat-dialog-title>{{ 'base_entity.working_context.switch_title' | transloco: { entity: data.entityLabel } }}</h2>
    <mat-dialog-content>
      <pp-working-context-picker [contextName]="data.contextName" (picked)="dialogRef.close($event)" />
    </mat-dialog-content>
  `,
})
export class WorkingContextPickerDialog {
  protected readonly data = inject<WorkingContextPickerDialogData>(MAT_DIALOG_DATA);
  protected readonly dialogRef = inject(MatDialogRef<WorkingContextPickerDialog>);
}
