import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
import { BaseRule } from '../domain/base-rule';
import { BaseRuleStore } from '../domain/base-rule.store';
import { createBaseRuleDescriptor } from '../domain/base-rule.descriptors';

@Component({
  selector: 'pp-base-rule-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent],
  template: `<base-entity-container [entityDescriptor]="baseEntityDescriptor"></base-entity-container>`,
})
export class BaseRuleContainerComponent implements OnDestroy {
  private readonly containerComponentRef: ComponentRef<BaseEntityContainerComponent> | undefined;
  @ViewChild(BaseFormHostDirective, { static: true, read: BaseFormHostDirective }) baseEntityHost!: BaseFormHostDirective;
  private readonly store = inject(BaseRuleStore);
  readonly baseEntityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.baseEntityDescriptor = createBaseRuleDescriptor();
    this.baseEntityDescriptor.store = this.store;
    this.baseEntityDescriptor.entityTitle = () => (this.store.currentEntity() as BaseRule)?.name ?? '';
  }

  ngOnDestroy(): void {
    if (this.containerComponentRef) {
      this.containerComponentRef.destroy();
    }
  }
}
