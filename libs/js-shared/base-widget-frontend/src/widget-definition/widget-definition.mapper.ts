import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { InputPort, OutputPort, PropsSchema, WidgetDefinition, WidgetDefinitionStatus } from './widget-definition';

interface WidgetDefinitionDto {
  key?: string;
  name?: string;
  translocoId?: string;
  description?: string;
  category?: string;
  icon?: string;
  propsSchema?: PropsSchema;
  inputPorts?: InputPort[];
  outputPorts?: OutputPort[];
  orgKey?: string;
  status?: WidgetDefinitionStatus;
  version?: number;
  publishedVersion?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between the `WidgetDefinition` DTO of `base-widget-api.yaml` and the entity the generated
 * form works with. One renaming — `key` becomes `id`, see {@link WidgetDefinition.id} — and otherwise a
 * field-for-field copy.
 *
 * **Every contract field is carried in both directions, whether or not a descriptor renders it.** That is
 * the invariant this class exists for, and it is load-bearing rather than tidy: `propsSchema` is
 * deliberately absent from the authoring form (see `widget-definition.descriptors.ts`), and the form saves
 * `{ ...loadedEntity, ...formValue }`, so an unrendered field survives the *form* — but `update` PUTs the
 * whole `WidgetDefinitionInput` and the server does not merge. A field this mapper dropped would therefore
 * be destroyed by the next Save of an unrelated field. `widget-definition.mapper.spec.ts` asserts the
 * round trip on a payload with a deeply nested schema for exactly that reason.
 */
@Injectable({ providedIn: 'root' })
export class WidgetDefinitionMapper implements BaseEntityMapper<WidgetDefinition> {
  fromDto(dto: unknown): WidgetDefinition {
    const source = dto as WidgetDefinitionDto;
    return new WidgetDefinition({
      id: source.key,
      name: source.name,
      translocoId: source.translocoId,
      description: source.description,
      category: source.category,
      icon: source.icon,
      propsSchema: source.propsSchema,
      inputPorts: source.inputPorts,
      outputPorts: source.outputPorts,
      orgKey: source.orgKey,
      status: source.status,
      version: source.version,
      publishedVersion: source.publishedVersion,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: WidgetDefinition): WidgetDefinitionDto {
    // Field by field rather than spread, so `id` does not travel beside the `key` it became.
    return {
      key: entity.id,
      name: entity.name,
      translocoId: entity.translocoId,
      description: entity.description,
      category: entity.category,
      icon: entity.icon,
      propsSchema: entity.propsSchema,
      inputPorts: entity.inputPorts,
      outputPorts: entity.outputPorts,
      orgKey: entity.orgKey,
      status: entity.status,
      version: entity.version,
      publishedVersion: entity.publishedVersion,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}
