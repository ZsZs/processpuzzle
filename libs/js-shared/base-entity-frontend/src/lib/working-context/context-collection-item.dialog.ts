import { Component, inject, OnInit, signal, ViewChild, ViewContainerRef } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogContent, MatDialogRef, MatDialogTitle } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { BaseEntityFormBuilder } from '../base-form/base-entity-form.builder';
import { CollectionItem } from './embedded-collection.service';

export interface ContextCollectionItemDialogData {
  descriptor: BaseEntityDescriptor;
  /** The item being edited; `undefined` for a new one. */
  item?: CollectionItem;
  /** The embedded entity's store, which some form controls ask for; absent when none is registered. */
  store?: unknown;
}

/**
 * One item of an embedded collection in a form of its own — the controls the entity's descriptor declares, built by the
 * same form builder the entity forms use. Closes with the values the form holds (the disabled controls left out, as in
 * an entity form's save), or with nothing when cancelled.
 */
@Component({
  selector: 'pp-context-collection-item-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogTitle, MatDialogContent, MatDialogActions, MatButton, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ (data.item ? 'base_entity.context_collection.edit' : 'base_entity.context_collection.add') | transloco }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" data-testid="context-collection-item-form">
        <ng-container #host />
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" mat-button data-testid="context-collection-item-cancel" (click)="dialogRef.close()">{{ 'base_entity.context_collection.cancel' | transloco }}</button>
      <button type="button" mat-flat-button data-testid="context-collection-item-save" [disabled]="form.invalid" (click)="onSave()">{{ 'base_entity.context_collection.save' | transloco }}</button>
    </mat-dialog-actions>
  `,
})
export class ContextCollectionItemDialog implements OnInit {
  protected readonly data = inject<ContextCollectionItemDialogData>(MAT_DIALOG_DATA);
  protected readonly dialogRef = inject(MatDialogRef<ContextCollectionItemDialog, CollectionItem>);
  protected readonly form = new FormGroup({});
  @ViewChild('host', { static: true, read: ViewContainerRef }) private readonly host!: ViewContainerRef;

  private readonly formBuilder = inject(BaseEntityFormBuilder);

  ngOnInit(): void {
    const initial = this.data.item ?? {};
    this.formBuilder.buildForm(
      this.host,
      this.form,
      (this.data.store ?? {}) as BaseEntityStoreApi<BaseEntity>,
      this.data.descriptor.attrDescriptors,
      signal(initial as BaseEntity),
      this.data.descriptor.entityName,
      initial,
    );
  }

  protected onSave(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.dialogRef.close({ ...this.data.item, ...this.form.value });
  }
}
