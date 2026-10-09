import { InjectionToken, Type } from '@angular/core';
import type { BaseEntityDescriptor } from '../../base-entity/base-entity.descriptor';

/**
 * What renders a {@link FormControlType.STATE} attribute, and which attribute of an entity is one.
 *
 * base-entity knows that some attribute *holds* a state; it cannot know the machine behind it — that is
 * base-state's, and base-entity does not depend on it. So the feature that owns the machines registers this,
 * the same way it contributes its tab through `ENTITY_TAB_CONTRIBUTORS`, and nothing on either side names the
 * other. Without a registration a STATE attribute is shown read-only as a label.
 */
export interface EntityStateControl {
  /** A `BaseFormControlComponent` subclass. Typed loosely: a stricter type would close an import cycle. */
  component: Type<unknown>;
  /**
   * The name of the attribute holding the state of `descriptor`'s objects, or `undefined` when no machine
   * governs that entity — by far the common answer. {@link EntityScreenResolver} asks this once per resolved
   * entity and turns that attribute into a STATE control, so a governed entity needs no descriptor change.
   */
  stateAttributeOf(descriptor: BaseEntityDescriptor): Promise<string | undefined> | string | undefined;
}

export const ENTITY_STATE_CONTROL = new InjectionToken<EntityStateControl>('ENTITY_STATE_CONTROL');
