import { Component, OnInit, ViewChild, ViewContainerRef, input, signal } from '@angular/core';
import { NgClass, NgStyle } from '@angular/common';
import { FormArray, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList } from '@angular/cdk/drag-drop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatAccordion, MatExpansionPanel, MatExpansionPanelDescription, MatExpansionPanelHeader, MatExpansionPanelTitle } from '@angular/material/expansion';
import { BaseFormControlComponent } from '../base-form-control.component';
import { BaseEntity } from '../../base-entity/base-entity';
import { AbstractAttrDescriptor } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityFormBuilder } from '../base-entity-form.builder';
import { BaseEntityStoreApi } from '../../base-entity-store/base-entity.store';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';

/**
 * Hosts the dynamic child form for a single row of an {@link EmbeddedListComponent}.
 */
@Component({
  selector: 'app-embedded-list-row',
  standalone: true,
  template: `<ng-container #rowHost />`,
})
export class EmbeddedListRowComponent<Entity extends BaseEntity> implements OnInit {
  @ViewChild('rowHost', { static: true, read: ViewContainerRef }) rowHost!: ViewContainerRef;

  readonly rowGroup = input.required<FormGroup>();
  readonly item = input<Record<string, unknown>>({});
  readonly childDescriptors = input.required<AbstractAttrDescriptor[]>();
  readonly store = input.required<BaseEntityStoreApi<Entity>>();
  readonly formBuilder = input.required<BaseEntityFormBuilder<Entity>>();
  readonly entityName = input.required<string>();
  readonly disabled = input<boolean>(false);

  ngOnInit(): void {
    const initial = this.item() ?? {};
    this.formBuilder().buildForm(
      this.rowHost,
      this.rowGroup(),
      this.store(),
      this.childDescriptors(),
      signal(initial as Entity),
      this.entityName(),
      initial,
    );
    if (this.disabled()) {
      this.rowGroup().disable();
    }
  }
}

interface EmbeddedListItem {
  id: string;
  formGroup: FormGroup;
  initialValue: Record<string, unknown>;
}

/**
 * Form control for an array of structured objects (`EMBEDDED_LIST`).
 *
 * Renders each item in a collapsible expansion panel with drag reordering, move buttons, and delete.
 * New items can be added up to `maxOccurs` (derived from `maxItems`).
 * The collapsed panel is titled by the attribute named in `config().rowLabelAttr` (or the item's first
 * required string property).
 */
@Component({
  selector: 'app-embedded-list',
  standalone: true,
  template: `
    @if (config().visible) {
      <div class="row" [formGroup]="formGroup">
        <fieldset class="base-entity-form-field" tabindex="0" [ngClass]="config().styleClass" [ngStyle]="config().style">
          <legend [ngClass]="config().labelClass">{{ config().i18nKey() | ppLabel: config().label }}</legend>
          <mat-accordion
            [id]="config().attrName"
            class="embedded-list-accordion"
            multi
            cdkDropList
            [cdkDropListDisabled]="config().disabled || rows().length <= 1"
            (cdkDropListDropped)="dropRow($event)"
          >
            @for (row of rows(); track row.id; let index = $index, first = $first, last = $last) {
              <mat-expansion-panel
                cdkDrag
                cdkDragLockAxis="y"
                [cdkDragDisabled]="config().disabled || rows().length <= 1"
                class="embedded-list-panel"
                [expanded]="true"
              >
                <mat-expansion-panel-header>
                  <mat-panel-title class="embedded-list-panel-title">
                    @if (!config().disabled && rows().length > 1) {
                      <mat-icon
                        cdkDragHandle
                        class="base-entity-form-drag-handle"
                        data-testid="embedded-row-drag-handle"
                        (click)="$event.stopPropagation()"
                      >
                        drag_indicator
                      </mat-icon>
                    }
                    <span>{{ rowLabel(row, index) }}</span>
                  </mat-panel-title>
                  @if (!config().disabled) {
                    <mat-panel-description class="embedded-list-panel-actions" (click)="$event.stopPropagation()">
                      @if (rows().length > 1) {
                        <button
                          type="button"
                          mat-icon-button
                          class="base-entity-form-row-button"
                          data-testid="embedded-row-move-up"
                          [disabled]="first"
                          [attr.aria-label]="'Move up'"
                          (click)="moveRow(index, index - 1)"
                        >
                          <mat-icon>arrow_upward</mat-icon>
                        </button>
                        <button
                          type="button"
                          mat-icon-button
                          class="base-entity-form-row-button"
                          data-testid="embedded-row-move-down"
                          [disabled]="last"
                          [attr.aria-label]="'Move down'"
                          (click)="moveRow(index, index + 1)"
                        >
                          <mat-icon>arrow_downward</mat-icon>
                        </button>
                      }
                      <button
                        type="button"
                        mat-icon-button
                        class="base-entity-form-delete-button"
                        data-testid="embedded-row-delete"
                        [attr.aria-label]="'Delete row'"
                        (click)="deleteRow(index)"
                      >
                        <mat-icon>delete</mat-icon>
                      </button>
                    </mat-panel-description>
                  }
                </mat-expansion-panel-header>

                <div class="embedded-list-row-body">
                  <app-embedded-list-row
                    [rowGroup]="row.formGroup"
                    [item]="row.initialValue"
                    [childDescriptors]="config().childDescriptors ?? []"
                    [store]="store"
                    [formBuilder]="formBuilder"
                    [entityName]="entityName()"
                    [disabled]="config().disabled"
                  />
                </div>
              </mat-expansion-panel>
            }
          </mat-accordion>

          @if (!config().disabled && (!config().maxOccurs || rows().length < config().maxOccurs!)) {
            <button
              type="button"
              mat-button
              class="base-entity-form-focus-action"
              data-testid="embedded-list-add"
              (click)="addRow()"
            >
              <mat-icon>add</mat-icon>
              Add {{ config().label }}
            </button>
          }
        </fieldset>
      </div>
    }
  `,
  styleUrls: ['../base-entity-form.css'],
  styles: [
    `
      .embedded-list-accordion {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-top: 4px;
        margin-bottom: 8px;
      }
      .embedded-list-panel {
        border: 1px solid var(--mat-sys-outline-variant, rgba(0, 0, 0, 0.12));
        border-radius: 4px;
      }
      .embedded-list-panel-title {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .embedded-list-panel-actions {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 4px;
        margin-left: auto;
      }
      .base-entity-form-drag-handle {
        cursor: grab;
        opacity: 0.6;
      }
      .embedded-list-row-body {
        padding-top: 8px;
      }
    `,
  ],
  imports: [
    NgClass,
    NgStyle,
    ReactiveFormsModule,
    CdkDropList,
    CdkDrag,
    CdkDragHandle,
    MatAccordion,
    MatExpansionPanel,
    MatExpansionPanelHeader,
    MatExpansionPanelTitle,
    MatExpansionPanelDescription,
    MatButton,
    MatIconButton,
    MatIcon,
    EntityLabelPipe,
    EmbeddedListRowComponent,
  ],
})
export class EmbeddedListComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  readonly rows = signal<EmbeddedListItem[]>([]);

  ngOnInit(): void {
    const rawArray = this.formGroup.get(this.config().attrName);
    const formArray = rawArray instanceof FormArray ? (rawArray as FormArray<FormGroup>) : new FormArray<FormGroup>([]);
    if (formArray !== rawArray) {
      this.formGroup.setControl(this.config().attrName, formArray);
    }

    const raw = this.value();
    const initialItems = (Array.isArray(raw) ? raw : []) as Record<string, unknown>[];
    const items: EmbeddedListItem[] = [];

    for (const item of initialItems) {
      const rowGroup = new FormGroup({});
      formArray.push(rowGroup);
      items.push({
        id: crypto.randomUUID(),
        formGroup: rowGroup,
        initialValue: item ?? {},
      });
    }

    this.rows.set(items);

    if (this.config().disabled) {
      formArray.disable();
    }
  }

  addRow(): void {
    const formArray = this.getFormArray();
    const rowGroup = new FormGroup({});
    formArray.push(rowGroup);
    formArray.markAsDirty();
    formArray.markAsTouched();

    this.rows.update((current) => [
      ...current,
      {
        id: crypto.randomUUID(),
        formGroup: rowGroup,
        initialValue: {},
      },
    ]);
  }

  deleteRow(index: number): void {
    const formArray = this.getFormArray();
    formArray.removeAt(index);
    formArray.markAsDirty();
    formArray.markAsTouched();

    this.rows.update((current) => current.filter((_, i) => i !== index));
  }

  moveRow(fromIndex: number, toIndex: number): void {
    const currentRows = this.rows();
    if (fromIndex < 0 || toIndex < 0 || fromIndex >= currentRows.length || toIndex >= currentRows.length || fromIndex === toIndex) {
      return;
    }

    const formArray = this.getFormArray();
    const ctrl = formArray.at(fromIndex);
    formArray.removeAt(fromIndex);
    formArray.insert(toIndex, ctrl);
    formArray.markAsDirty();
    formArray.markAsTouched();

    const newRows = [...currentRows];
    const [moved] = newRows.splice(fromIndex, 1);
    newRows.splice(toIndex, 0, moved);
    this.rows.set(newRows);
  }

  dropRow(event: CdkDragDrop<EmbeddedListItem[]>): void {
    this.moveRow(event.previousIndex, event.currentIndex);
  }

  private getFormArray(): FormArray<FormGroup> {
    const raw = this.formGroup.get(this.config().attrName);
    if (raw instanceof FormArray) {
      return raw as FormArray<FormGroup>;
    }
    const created = new FormArray<FormGroup>([]);
    this.formGroup.setControl(this.config().attrName, created);
    return created;
  }

  rowLabel(row: EmbeddedListItem, index: number): string {
    const rowLabelAttr = this.config().rowLabelAttr;
    if (rowLabelAttr) {
      const val = row.formGroup.get(rowLabelAttr)?.value ?? row.initialValue[rowLabelAttr];
      if (val !== undefined && val !== null && String(val).trim() !== '') {
        return String(val).trim();
      }
    }
    return `${this.config().label} #${index + 1}`;
  }
}
