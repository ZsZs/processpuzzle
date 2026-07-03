import { Component, computed, inject, InjectionToken, Injector, type ProviderToken, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { AbstractAttrDescriptor } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityFacade } from './base-entity-facade';
import { filterAttributeDescriptors } from '../base-entity/filter-attr-descriptor';
import { BaseEntity } from '../base-entity/base-entity';
import { EntityRouteRegistry } from '../base-form-navigator/entity-route.registry';

export type BaseEntityFacadeRegistry = Record<string, ProviderToken<BaseEntityFacade<BaseEntity>>>;

export const BASE_ENTITY_FACADE_REGISTRY = new InjectionToken<BaseEntityFacadeRegistry>('BASE_ENTITY_FACADE_REGISTRY', {
  factory: () => ({}),
});

@Component({
  selector: 'pp-entity-registry',
  template: ` <pre>{{ registryJson() }}</pre> `,
})
export class EntityRegistryComponent {
  private readonly registry = inject(BASE_ENTITY_FACADE_REGISTRY);
  private readonly injector = inject(Injector);
  private readonly entityRouteRegistry = inject(EntityRouteRegistry);
  private readonly queryParams = toSignal(inject(ActivatedRoute).queryParamMap, { initialValue: null });
  private readonly descriptors = signal(this.buildDescriptors());

  protected registryJson = computed(() => {
    const minified = this.queryParams()?.get('minified') === 'yes';
    return minified ? JSON.stringify(this.descriptors()) : JSON.stringify(this.descriptors(), null, 2);
  });

  private buildDescriptors() {
    return Object.values(this.registry).map((facadeToken) => {
      const facade = this.injector.get(facadeToken);
      const descriptor = facade.descriptor;
      const attrDescriptors = filterAttributeDescriptors(facade.descriptor.attrDescriptors);
      const entityTitle = typeof descriptor.entityTitle === 'function' ? descriptor.entityTitle() : descriptor.entityTitle;
      return {
        entityName: descriptor.entityName,
        entityTitle,
        isAbstract: descriptor.isAbstract,
        parentEntityName: descriptor.parentEntity,
        route: this.entityRouteRegistry.basePath(descriptor.entityName),
        attrDescriptors: attrDescriptors.map((attr) => this.serializeAttr(attr)),
      };
    });
  }

  private serializeAttr(attr: AbstractAttrDescriptor) {
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
        selectables: attr.getSelectables(),
        visible: attr.visible,
        hideInTable: attr.hideInTable,
        isHeading: attr.isHeading,
        placeholder: attr.placeholder,
        lines: attr.lines,
        options: attr.options,
        required: attr.required,
        linkedEntityType: attr.linkedEntityType,
      }),
    };
  }
}
