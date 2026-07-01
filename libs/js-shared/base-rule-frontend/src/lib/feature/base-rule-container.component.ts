import { Component, ComponentRef, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BASE_ENTITY_FACADE_REGISTRY, BaseEntityContainerComponent, BaseEntityDescriptor, BaseFormHostDirective } from '@processpuzzle/base-entity';
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
  private readonly entityRegistry = inject(BASE_ENTITY_FACADE_REGISTRY);
  readonly baseEntityDescriptor: BaseEntityDescriptor;

  constructor() {
    this.baseEntityDescriptor = createBaseRuleDescriptor(() => Object.keys(this.entityRegistry).map((name) => ({ key: name, value: name })));
    this.baseEntityDescriptor.store = this.store;
    this.baseEntityDescriptor.entityTitle = () => (this.store.currentEntity() as BaseRule)?.name ?? '';
  }

  ngOnDestroy(): void {
    if (this.containerComponentRef) {
      this.containerComponentRef.destroy();
    }
  }
}
