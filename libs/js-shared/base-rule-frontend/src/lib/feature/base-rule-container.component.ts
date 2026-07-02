import { Component, ComponentRef, inject, OnDestroy, OnInit, TemplateRef, viewChild, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import {
  BASE_ENTITY_FACADE_REGISTRY,
  BaseEntityContainerComponent,
  BaseEntityDescriptor,
  BaseFormHostDirective,
  BaseFormNavigatorSingletonStore,
  NavigatorCommand,
  type Selectable,
} from '@processpuzzle/base-entity';
import { BaseRule } from '../domain/base-rule';
import { BaseRuleStore } from '../domain/base-rule.store';
import { createBaseRuleDescriptor } from '../domain/base-rule.descriptors';
import { BaseRuleDryRunDialog, BaseRuleDryRunDialogData, BaseRuleDryRunDialogResult } from './base-rule-dry-run.dialog';

const DRY_RUN_ATTR = 'baseRuleDryRun';

@Component({
  selector: 'pp-base-rule-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent, MatButton],
  template: `
    <base-entity-container [entityDescriptor]="baseEntityDescriptor"></base-entity-container>
    <ng-template #dryRunActionsTpl>
      <button id="dry-run" type="button" mat-raised-button color="accent" (click)="onDryRun()">Dry run...</button>
    </ng-template>
  `,
})
export class BaseRuleContainerComponent implements OnInit, OnDestroy {
  private readonly containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  readonly dryRunActionsTpl = viewChild<TemplateRef<unknown>>('dryRunActionsTpl');
  private readonly store = inject(BaseRuleStore);
  private readonly entityRegistry = inject(BASE_ENTITY_FACADE_REGISTRY);
  private readonly dialog = inject(MatDialog);
  private readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly contextOptions: ReadonlyArray<Selectable>;
  readonly baseEntityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.contextOptions = Object.keys(this.entityRegistry).map((name) => ({ key: name, value: name }));
    this.baseEntityDescriptor = createBaseRuleDescriptor(() => this.contextOptions as Array<Selectable>);
    this.baseEntityDescriptor.store = this.store;
    this.baseEntityDescriptor.entityTitle = () => (this.store.currentEntity() as BaseRule)?.name ?? '';
    this.baseEntityDescriptor.extraFormActionsTemplate = () => this.dryRunActionsTpl();
  }

  ngOnInit(): void {
    const pending = this.formNavigator.popResponsePayload(DRY_RUN_ATTR);
    if (pending?.command === NavigatorCommand.SELECT_OR_CREATE && pending.payload) {
      this.openDialog(pending.payload);
    }
  }

  ngOnDestroy(): void {
    if (this.containerComponentRef) {
      this.containerComponentRef.destroy();
    }
  }

  onDryRun(): void {
    this.openDialog();
  }

  private openDialog(prefilledEntity?: unknown): void {
    const rule = this.store.currentEntity() as BaseRule | undefined;
    if (!rule) return;
    const dialogRef = this.dialog.open<BaseRuleDryRunDialog, BaseRuleDryRunDialogData, BaseRuleDryRunDialogResult>(BaseRuleDryRunDialog, {
      data: { rule, prefilledEntity },
      width: '640px',
    });
    dialogRef.afterClosed().subscribe(async (result) => {
      if (result?.action === 'pick' && rule.context) {
        await this.formNavigator.navigateToRelatedList(rule.context, this.formNavigator.determineCurrentUrl(), {
          command: NavigatorCommand.SELECT_OR_CREATE,
          attrName: DRY_RUN_ATTR,
        });
      }
    });
  }
}
