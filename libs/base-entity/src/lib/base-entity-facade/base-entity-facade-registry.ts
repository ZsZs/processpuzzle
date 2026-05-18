import { Component, computed, inject, InjectionToken, Injector, type ProviderToken, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityFacade } from './base-entity-facade';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';

export type BaseEntityFacadeRegistry = Record<string, ProviderToken<BaseEntityFacade<any>>>;

export const BASE_ENTITY_FACADE_REGISTRY = new InjectionToken<BaseEntityFacadeRegistry>('BASE_ENTITY_FACADE_REGISTRY', {
  factory: () => ({}),
});

@Component({
  selector: 'pp-entity-registry',
  template: ` <pre>{{ registryJson() }}</pre> `,
})
export class EntityRegistryComponent {
  private registry = inject(BASE_ENTITY_FACADE_REGISTRY);
  private injector = inject(Injector);
  private queryParams = toSignal(inject(ActivatedRoute).queryParamMap, { initialValue: null });

  private descriptors = signal(this.buildDescriptors());

  protected registryJson = computed(() => {
    const minified = this.queryParams()?.get('minified') === 'yes';
    return minified ? JSON.stringify(this.descriptors(), null, 2) : JSON.stringify(this.descriptors(), null, 2);
  });

  private buildDescriptors() {
    return Object.entries(this.registry).map(([, facadeToken]) => {
      const facade = this.injector.get(facadeToken);
      const descriptor = facade.descriptor;
      const attrDescriptors = filterAttributeDescriptors(facade.descriptor.attrDescriptors);
      return {
        entityName: descriptor.entityName,
        entityTitle: descriptor.entityTitle,
        attrDescriptors: attrDescriptors.map((attr) => this.serializeAttr(attr)),
      };
    });
  }

  private serializeAttr(attr: any) {
    const isBaseAttr = attr instanceof BaseEntityAttrDescriptor;
    return {
      attrName: attr.attrName,
      formControlType: attr.formControlType,
      disabled: attr.disabled,
      style: attr.style,
      ...(isBaseAttr && {
        label: attr.label,
        description: attr.description,
        styleClass: attr.styleClass,
        labelClass: attr.labelClass,
        format: attr.format,
        isLinkToDetails: attr.isLinkToDetails,
        selectables: attr.selectables,
        visible: attr.visible,
        hideInTable: attr.hideInTable,
        isHeading: attr.isHeading,
        placeholder: attr.placeholder,
        lines: attr.lines,
        options: attr.options,
        required: attr.required,
        linkedEntityType: attr.linkedEntityType ? { entityName: attr.linkedEntityType.entityName } : undefined,
      }),
    };
  }
}
