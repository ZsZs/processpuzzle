import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { AppDefinition, AppDefinitionStatus, ColorScheme, LayoutDefinition, LayoutPreset, MaterialTheme, SidenavMode, ThemeDefinition } from './app-definition';

interface AppDefinitionDto {
  id?: string;
  name?: string;
  translocoId?: string;
  description?: string;
  theme?: ThemeDefinition;
  layout?: LayoutDefinition;
  regions?: AppDefinition['regions'];
  pages?: AppDefinition['pages'];
  orgKey?: string;
  status?: AppDefinitionStatus;
  version?: number;
  publishedVersion?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between the `AppDefinition` DTO of `base-app-api.yaml` and the flat entity the
 * generated form works with.
 *
 * Two invariants matter here. The form saves `{ ...entity, ...form.value }`, so every DTO field the
 * form does not show has to survive on the entity — `regions` and `pages` are nested rather than
 * flattened for exactly that reason, and the `EMBEDDED_COMPONENTS` controls edit them in place. And
 * `PUT /app-definitions/{appId}` is a full replacement, so {@link toDto} rebuilds `theme` and
 * `layout` from the flattened controls *on top of* the objects they were lifted out of, which keeps
 * any field a later contract version adds before this mapper learns about it.
 */
@Injectable({ providedIn: 'root' })
export class AppDefinitionMapper implements BaseEntityMapper<AppDefinition> {
  fromDto(dto: unknown): AppDefinition {
    const source = dto as AppDefinitionDto;
    const theme = source.theme;
    const layout = source.layout;
    return new AppDefinition({
      id: source.id,
      name: source.name,
      translocoId: source.translocoId,
      description: source.description,
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
      regions: source.regions,
      pages: source.pages,
      orgKey: source.orgKey,
      status: source.status,
      version: source.version,
      publishedVersion: source.publishedVersion,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: AppDefinition): unknown {
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
