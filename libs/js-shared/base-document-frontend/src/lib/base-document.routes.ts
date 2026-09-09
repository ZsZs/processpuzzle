import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { baseEntityRoutes, type EmbeddedChildRoute } from '@processpuzzle/base-entity';
import { BASE_DOCUMENT_TRANSLOCO_SCOPE, BASE_ENTITY_TRANSLOCO_SCOPE } from './base-document.i18n';
import { DOCUMENT_ENTITY_NAME, DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME } from './domain/document-entity-names';
import { BaseDocumentContainerComponent } from './feature/base-document-container.component';
import { DOCUMENT_CONTENT_TAB } from './feature/document-content-tab';
import { DocumentInputPortFacade } from './feature/document-input-port.facade';
import { DocumentOutputPortFacade } from './feature/document-output-port.facade';

/**
 * The path segment has to be `snakeCaseName('Document')` — `document`, singular — because
 * `BaseFormNavigatorSingletonStore` builds the list and details URLs from the entity name rather than from
 * the route: it strips the current URL back to the parent and re-appends the snake-cased name. A plural
 * `documents` segment would render the list and then 404 on the first row clicked. Same constraint as on
 * `BASE_APP_ROUTES` and `BASE_RULE_ROUTES`; the plural stays in the menu label, which is free text.
 */
export const BASE_DOCUMENT_ROUTES: Routes = [
  {
    path: 'document',
    title: 'ProcessPuzzle Design - Documents',
    data: { icon: 'article', menuTitle: 'design.documents', entityName: DOCUMENT_ENTITY_NAME },
    component: BaseDocumentContainerComponent,
    // Provided here rather than on the container, so the base-entity tabs, list and form rendered in the
    // child routes resolve the entity and attribute labels from the same scopes. Both are needed: a route
    // that declares TRANSLOCO_SCOPE replaces the collection it inherits rather than adding to it, and the
    // generic tabs translate the framework's own `base_entity.*` keys (see BASE_ENTITY_TRANSLOCO_SCOPE).
    // The embedded branches below need none of their own: `base_document.document_input_port.*` and its
    // sibling are keys of the scope already registered here.
    providers: [provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_DOCUMENT_TRANSLOCO_SCOPE, alias: BASE_DOCUMENT_TRANSLOCO_SCOPE })],
    // The content tab's own route, `document/<id>/content`, comes from the second argument — the same
    // constant BaseDocumentContainerComponent puts on the descriptor, so the tab link and the route that
    // answers it are one declaration.
    children: baseEntityRoutes(embeddedPortRoutes(), [DOCUMENT_CONTENT_TAB]),
  },
];

/**
 * The two port lists as route branches, hanging below the document's details route.
 *
 * Below it rather than beside it because an embedded row has no id of its own to be looked up by — the URL,
 * `document/getting-started/details/document-input-port/orderId/details`, is what addresses it, and each
 * segment resolves against the rows of the level above. Neither port nests further: a port is a leaf, its
 * `attributeVisibility` a plain nested object edited in place by an `ADDITIONAL_PROPERTIES` control.
 */
function embeddedPortRoutes(): EmbeddedChildRoute[] {
  return [
    { entityName: DOCUMENT_INPUT_PORT_ENTITY_NAME, facade: DocumentInputPortFacade },
    { entityName: DOCUMENT_OUTPUT_PORT_ENTITY_NAME, facade: DocumentOutputPortFacade },
  ];
}
