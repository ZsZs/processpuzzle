import type { AppDefinition } from './app-definition';

/**
 * The types of the widgets an application's regions hold — what its header, footer and the like already carry. A host
 * that adds controls of its own to the shell asks it, so that a control the designer placed is not drawn twice.
 */
export function regionWidgetTypes(definition: Pick<AppDefinition, 'regions'> | undefined): ReadonlySet<string> {
  return new Set((definition?.regions ?? []).flatMap((region) => region.widgets ?? []).map((widget) => widget.type));
}
