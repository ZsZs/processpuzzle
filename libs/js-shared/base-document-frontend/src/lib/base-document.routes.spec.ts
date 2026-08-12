import { Route, Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { BASE_DOCUMENT_ROUTES } from './base-document.routes';
import { DOCUMENT_CONTENT_TAB } from './feature/document-content-tab';

/** The branches an embedded level mounts, expanded one navigation at a time by `loadChildren`. */
async function embeddedBranchesOf(route: Route | undefined): Promise<Routes> {
  const loadChildren = route?.loadChildren;
  return loadChildren ? ((await loadChildren()) as Routes) : [];
}

describe('BASE_DOCUMENT_ROUTES', () => {
  const [documentRoute] = BASE_DOCUMENT_ROUTES;
  const detailsRoute = documentRoute.children?.find((child) => child.path === ':entityId/details');

  /**
   * Singular on purpose. `BaseFormNavigatorSingletonStore` re-appends `snakeCaseName(entityName)` to the
   * parent URL instead of reading the route, so a plural segment would list the documents and 404 on the
   * first row opened — and the design card's `/design/document` link has to agree with it.
   */
  it('uses the snake-cased entity name as path, as the form navigator expects', () => {
    expect(documentRoute.path).toBe('document');
  });

  it('advertises itself to the design sidenav and to the entity route registry', () => {
    expect(documentRoute.title).toBeTruthy();
    expect(documentRoute.data).toEqual({ icon: 'article', menuTitle: 'design.documents', entityName: 'Document' });
  });

  it('registers both the generic and this library scope for itself and its children', () => {
    // base_entity is not inherited: the generic tabs resolve `base_entity.tabs.*` and a route that declares
    // TRANSLOCO_SCOPE replaces the inherited collection. The aliases are asserted too, because left to
    // transloco's default they would be camel-cased (`baseDocument`, `baseEntity`) and no key would resolve.
    const scopeProviders = (documentRoute.providers?.flat() ?? []) as Array<{ useValue: unknown }>;

    expect(scopeProviders.map((provider) => provider.useValue)).toEqual([
      { scope: 'base_entity', alias: 'base_entity' },
      { scope: 'base_document', alias: 'base_document' },
    ]);
  });

  it('nests the generic list and details routes below the container', () => {
    expect(documentRoute.component).toBeDefined();
    expect(documentRoute.children?.map((child) => child.path)).toEqual(['', ':entityId/details', ':entityId/content', 'list']);
  });

  /**
   * A sibling of the details route, sharing its `<entity>/<id>` prefix — that shape is what
   * BaseFormNavigatorSingletonStore counts back over to build every other URL of this entity, and what makes
   * the content of one document addressable on its own. Same constant the container puts on the descriptor,
   * so the tab link and this route cannot name different segments.
   */
  it('gives the content editor a route of its own', () => {
    const contentRoute = documentRoute.children?.find((child) => child.path === ':entityId/content');

    expect(contentRoute?.component).toBe(DOCUMENT_CONTENT_TAB.component);
    expect(DOCUMENT_CONTENT_TAB.segment).toBe('content');
  });

  it('hangs both port lists below the document being edited', async () => {
    const branches = await embeddedBranchesOf(detailsRoute);

    // Below the details route, not beside it: an embedded row has no id to be looked up by, so the
    // owner's segments are what address it — and what make it unreachable except through the owner.
    expect(branches.map((branch) => branch.path)).toEqual(['document-input-port', 'document-output-port']);
    expect(branches.map((branch) => branch.data?.['entityName'])).toEqual(['DocumentInputPort', 'DocumentOutputPort']);
    branches.forEach((branch) => expect(branch.data?.['embeddedEntity']).toBe(true));
  });

  it('stops at the ports, which are leaves', async () => {
    const branches = await embeddedBranchesOf(detailsRoute);

    for (const branch of branches) {
      const portDetails = (await embeddedBranchesOf(branch)).find((route) => route.path === ':entityId/details');
      expect(await embeddedBranchesOf(portDetails)).toEqual([]);
    }
  });

  it('gives an embedded level a details form and no list of its own', async () => {
    const [inputPortBranch] = await embeddedBranchesOf(detailsRoute);

    // The rows are already listed on the owner's form, which is also the only place they are reachable
    // from, so a list route here would be a second door to the same room.
    expect((await embeddedBranchesOf(inputPortBranch)).map((route) => route.path)).toEqual([':entityId/details']);
  });
});
