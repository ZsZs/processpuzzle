import { Component, inject, Injector, input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { RsqlEditorDialog, RsqlEditorDialogData } from '../query-editor/rsql-editor.dialog';
import { DescriptorBackedFieldMetadataProvider } from '../query-editor/descriptor-backed-field-metadata.provider';
import { RsqlFieldMetadataProvider } from '../query-editor/rsql-field-metadata.model';

@Component({
  selector: 'base-entity-query',
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormField, MatInput, MatIcon, MatIconButton, MatSuffix, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
  template: `
    <ng-container *transloco="let t">
      <div class="query-wrapper">
        <label class="field-label" [attr.for]="entityDescriptor().createTestId('query-input')">{{ t('base_entity.toolbar.query_label') }}</label>
        <mat-form-field class="query-field" subscriptSizing="dynamic">
          <input
            [id]="entityDescriptor().createTestId('query-input')"
            [attr.data-testid]="entityDescriptor().createTestId('query')"
            matInput
            type="text"
            [(ngModel)]="queryValue"
            (keyup.enter)="onSendQuery()"
          />
          <button [attr.data-testid]="entityDescriptor().createTestId('query-editor-open')" type="button" mat-icon-button matSuffix aria-label="Open advanced query editor" (click)="onOpenEditor()">
            <mat-icon>edit_note</mat-icon>
          </button>
          <button [attr.data-testid]="entityDescriptor().createTestId('query-send')" type="button" mat-icon-button matSuffix aria-label="Send query" [disabled]="!queryValue()" (click)="onSendQuery()">
            <mat-icon>play_arrow</mat-icon>
          </button>
        </mat-form-field>
      </div>
    </ng-container>
  `,
  styles: [
    `
      .query-wrapper {
        display: inline-flex;
        align-items: center;
        gap: 8px;
      }
      .field-label {
        font-size: 14px;
        white-space: nowrap;
      }
      .query-field {
        width: 320px;
      }
    `,
  ],
})
export class BaseEntityQueryComponent<Entity extends BaseEntity> implements OnInit {
  entityDescriptor = input.required<BaseEntityDescriptor>();
  queryValue = signal<string>('');
  store!: BaseEntityStoreApi<Entity>;

  private readonly dialog = inject(MatDialog);
  private readonly parentInjector = inject(Injector);

  ngOnInit(): void {
    this.store = this.entityDescriptor().store as BaseEntityStoreApi<Entity>;
  }

  onSendQuery(): void {
    const value = this.queryValue().trim();
    if (!value) return;
    this.store.load({ query: value });
  }

  onOpenEditor(): void {
    const data: RsqlEditorDialogData = { initialQuery: this.queryValue() };
    const fieldMetadata = new DescriptorBackedFieldMetadataProvider(this.entityDescriptor().attrDescriptors);
    const injector = Injector.create({
      providers: [{ provide: RsqlFieldMetadataProvider, useValue: fieldMetadata }],
      parent: this.parentInjector,
    });
    this.dialog
      .open<RsqlEditorDialog, RsqlEditorDialogData, string | null>(RsqlEditorDialog, { data, injector })
      .afterClosed()
      .subscribe((result) => {
        if (result != null) this.queryValue.set(result);
      });
  }
}
