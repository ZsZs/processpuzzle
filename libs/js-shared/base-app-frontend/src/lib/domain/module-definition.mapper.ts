import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { ModuleDefinition } from './module-definition';
import { flattenRouteTarget, nestRouteTarget, RouteDefinitionDto } from './route-definition.mapper';

interface ModuleDefinitionDto {
  key?: string;
  name?: string;
  translocoId?: string;
  description?: string;
  translocoScope?: string;
  routes?: RouteDefinitionDto[];
  orgKey?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between the `ModuleDefinition` DTO of `base-app-api.yaml` and the entity the generated form
 * works with. Two renamings, and nothing else — a module is a much smaller aggregate than an app:
 *
 * - **`key` becomes `id`.** base-entity keys every store, URL and reference on `id`, so the rename
 *   happens here rather than in the framework. See {@link ModuleDefinition.id}.
 * - **each route's `target` is flattened** onto the row and re-nested on save, by the same functions
 *   `AppDefinitionMapper` uses, so a route authored in a module behaves like one authored in an app.
 */
@Injectable({ providedIn: 'root' })
export class ModuleDefinitionMapper implements BaseEntityMapper<ModuleDefinition> {
  fromDto(dto: unknown): ModuleDefinition {
    const source = dto as ModuleDefinitionDto;
    return new ModuleDefinition({
      id: source.key,
      name: source.name,
      translocoId: source.translocoId,
      description: source.description,
      translocoScope: source.translocoScope,
      routes: source.routes?.map(flattenRouteTarget),
      orgKey: source.orgKey,
      version: source.version,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: ModuleDefinition): ModuleDefinitionDto {
    // Field by field rather than spread, so `id` does not travel beside the `key` it became.
    return {
      key: entity.id,
      name: entity.name,
      translocoId: entity.translocoId,
      description: entity.description,
      translocoScope: entity.translocoScope,
      routes: entity.routes?.map(nestRouteTarget),
      orgKey: entity.orgKey,
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}
