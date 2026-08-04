import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { AppDefinition, AppDefinitionStatus, ColorScheme, LayoutDefinition, LayoutPreset, MaterialTheme, SidenavMode, ThemeDefinition } from './app-definition';

/**
 * Translates between the `AppDefinition` DTO of `base-app-api.yaml` and the flat entity the
 * generated form works with.
 *
 * Two invariants matter here. The form saves `{ ...entity, ...form.value }`, so every DTO field the
 * form does not show has to survive on the entity — `regions` and `pages` are nested rather than
 * flattened for exactly that reason, and the `RELATED_ENTITIES` controls edit them in place. And
 * `PUT /app-definitions/{appId}` is a full replacement, so {@link toDto} rebuilds `theme` and
 * `layout` from the flattened controls *on top of* the objects they were lifted out of, which keeps
 * any field a later contract version adds before this mapper learns about it.
 */
@Injectable({ providedIn: 'root' })
export class AppDefinitionMapper implements BaseEntityMapper<AppDefinition> {
  fromDto(dto: any): AppDefinition {
    const theme: ThemeDefinition | undefined = dto?.theme;
    const layout: LayoutDefinition | undefined = dto?.layout;
    return new AppDefinition({
      id: dto?.id,
      name: dto?.name,
      translocoId: dto?.translocoId,
      description: dto?.description,
      materialTheme: theme?.materialTheme as MaterialTheme | undefined,
      colorScheme: theme?.colorScheme as ColorScheme | undefined,
      tokenOverrides: theme?.tokenOverrides,
      logoUrl: theme?.logoUrl,
      faviconUrl: theme?.faviconUrl,
      preset: layout?.preset as LayoutPreset | undefined,
      sidenavMode: layout?.sidenavMode as SidenavMode | undefined,
      sidenavCollapsible: layout?.sidenavCollapsible,
      sidenavOpenByDefault: layout?.sidenavOpenByDefault,
      contentMaxWidth: layout?.contentMaxWidth,
      theme,
      layout,
      regions: dto?.regions,
      pages: dto?.pages,
      orgKey: dto?.orgKey,
      status: dto?.status as AppDefinitionStatus | undefined,
      version: dto?.version,
      publishedVersion: dto?.publishedVersion,
      createdAt: dto?.createdAt,
      updatedAt: dto?.updatedAt,
    });
  }

  toDto(entity: AppDefinition): any {
    // Listed field by field rather than spread, so the flattened controls never reach the payload.
    return {
      id: entity.id,
      name: entity.name,
      translocoId: entity.translocoId,
      description: entity.description,
      theme: this.mergeTheme(entity),
      layout: this.mergeLayout(entity),
      regions: entity.regions,
      pages: entity.pages,
      orgKey: entity.orgKey,
      status: entity.status,
      version: entity.version,
      publishedVersion: entity.publishedVersion,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }

  private mergeTheme(entity: AppDefinition): ThemeDefinition {
    return {
      ...entity.theme,
      materialTheme: entity.materialTheme,
      colorScheme: entity.colorScheme,
      tokenOverrides: entity.tokenOverrides,
      logoUrl: entity.logoUrl,
      faviconUrl: entity.faviconUrl,
    };
  }

  private mergeLayout(entity: AppDefinition): LayoutDefinition {
    return {
      ...entity.layout,
      preset: entity.preset,
      sidenavMode: entity.sidenavMode,
      sidenavCollapsible: entity.sidenavCollapsible,
      sidenavOpenByDefault: entity.sidenavOpenByDefault,
      contentMaxWidth: entity.contentMaxWidth,
    };
  }
}
