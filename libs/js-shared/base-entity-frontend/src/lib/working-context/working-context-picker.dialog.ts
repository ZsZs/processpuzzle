import { Component, inject } from '@angular/core';
import { MatAnchor } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { EntityRouteRegistry } from '../base-form-navigator/entity-route.registry';
import { WorkingContextPickerComponent } from './working-context-picker.component';
import { WorkingContextService } from './working-context.service';

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
  imports: [MatAnchor, MatDialogTitle, MatDialogContent, TranslocoPipe, WorkingContextPickerComponent],
  template: `
    <h2 mat-dialog-title>{{ 'base_entity.working_context.switch_title' | transloco: { entity: data.entityLabel } }}</h2>
    @if (listPath()) {
      <a mat-button class="picker-list-link" data-testid="working-context-picker-list-link" (click)="onShowList()">{{ 'base_entity.working_context.show_list' | transloco }}</a>
    }
    <mat-dialog-content>
      <pp-working-context-picker [contextName]="data.contextName" (picked)="dialogRef.close($event)" />
    </mat-dialog-content>
  `,
})
export class WorkingContextPickerDialog {
  protected readonly data = inject<WorkingContextPickerDialogData>(MAT_DIALOG_DATA);
  protected readonly dialogRef = inject(MatDialogRef<WorkingContextPickerDialog>);
  private readonly router = inject(Router);
  private readonly routes = inject(EntityRouteRegistry);
  private readonly service = inject(WorkingContextService);

  /** Where the context entity's own list is, when it has one. */
  protected listPath(): string | undefined {
    const entityName = this.service.definitionOf(this.data.contextName)?.entityName;
    return entityName ? this.routes.listPath(entityName) : undefined;
  }

  /** Leaves the drawer for the full list of the context's entity. */
  protected async onShowList(): Promise<void> {
    const path = this.listPath();
    this.dialogRef.close();
    if (path) await this.router.navigateByUrl(path);
  }
}
