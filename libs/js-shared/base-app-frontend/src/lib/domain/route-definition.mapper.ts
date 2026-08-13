import { RouteDefinition, RouteTarget } from './app-definition';

/** The `RouteDefinition` schema as it travels: `target` nested, exactly as the contract declares it. */
export interface RouteDefinitionDto {
  path?: string;
  title?: string;
  translocoId?: string;
  icon?: string;
  roles?: string[];
  target?: RouteTarget;
}

/**
 * The `target` flattening both aggregates that own routes need — `AppDefinition.routes` and
 * `ModuleDefinition.routes` carry the same schema, and a route authored in a module has to behave
 * exactly like one authored in an app.
 *
 * Why flatten at all: `EMBEDDED_COMPONENTS` edits a row as the flat JSON it arrived as, and a descriptor
 * addresses one property rather than a path, so nothing in the generated form could reach
 * `target.documentSlug`. The contract already keeps `RouteTarget` flat rather than a `oneOf` for the
 * related reason that the form cannot edit a discriminated union of classes.
 */
export function flattenRouteTarget(dto: RouteDefinitionDto): RouteDefinition {
  const target = dto.target;
  return new RouteDefinition({
    path: dto.path,
    title: dto.title,
    translocoId: dto.translocoId,
    icon: dto.icon,
    roles: dto.roles,
    kind: target?.kind,
    widgets: target?.widgets,
    documentSlug: target?.documentSlug,
    entityName: target?.entityName,
    entityMode: target?.entityMode,
    rsqlFilter: target?.rsqlFilter,
    // Kept so nestRouteTarget can merge onto it rather than replace it.
    target,
  });
}

/**
 * The inverse. `kind` is non-optional in the contract but undefined on a row the designer has not
 * finished — the dropdown is `required`, so the form rejects that before a save; the cast keeps this
 * mapping from having to invent a default the backend would then have to second-guess.
 *
 * `widgets` is omitted when empty rather than sent as `[]`, so a DOCUMENT or ENTITY route does not
 * persist an empty widget list it has no use for. The rest is merged onto the `target` the fields were
 * lifted out of, which preserves any field a later contract version adds before this mapping learns
 * about it — a PUT is a full replacement, so anything dropped here is dropped for good.
 */
export function nestRouteTarget(route: RouteDefinition): RouteDefinitionDto {
  const widgets = route.widgets;
  return {
    path: route.path,
    title: route.title,
    translocoId: route.translocoId,
    icon: route.icon,
    roles: route.roles,
    target: {
      ...route.target,
      kind: route.kind as RouteTarget['kind'],
      widgets: widgets?.length ? widgets : undefined,
      documentSlug: route.documentSlug,
      entityName: route.entityName,
      entityMode: route.entityMode,
      rsqlFilter: route.rsqlFilter,
    },
  };
}
