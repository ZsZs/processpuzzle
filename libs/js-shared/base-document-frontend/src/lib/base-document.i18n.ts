/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-document-frontend/src/assets/i18n/base_document/*.json` and are published with
 * the package; a consuming application copies them to `assets/i18n/base_document` (see the testbed's
 * `project.json`). The scope is registered on {@link BASE_DOCUMENT_ROUTES}, so it loads lazily with the
 * route rather than with the application shell.
 *
 * Underscored rather than hyphenated, and the alias spelled out on purpose: transloco camel-cases the
 * default alias, which would turn `base_document` into `baseDocument` and silently miss every key below.
 */
export const BASE_DOCUMENT_TRANSLOCO_SCOPE = 'base_document';

/**
 * Scope of the generic framework labels (`base_entity.tabs.*`, `base_entity.toolbar.*`), whose files
 * base-entity owns. It has to be registered next to {@link BASE_DOCUMENT_TRANSLOCO_SCOPE} wherever the
 * generic screens are hosted: `BaseEntityTabsComponent` translates from it through `TranslocoPipe`,
 * which caches the value it resolved for a key on first render.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

/** Key root of the `Document` entity name (`._self`) and of its attribute labels. */
export const DOCUMENT_I18N_SCOPE = `${BASE_DOCUMENT_TRANSLOCO_SCOPE}.document`;

/**
 * Label of the Content tab. Under this library's own scope rather than `base_entity.tabs.*`: the generic
 * tabs are List and Details, and a content editor is this feature's contribution, not the framework's.
 * Resolved with `{ entity }` like the generic tab labels, so a translation may name the entity if it reads
 * better that way.
 */
export const DOCUMENT_CONTENT_I18N_KEY = `${DOCUMENT_I18N_SCOPE}.tabs.content`;

/**
 * Key roots of the two port shapes the `Document` form carries through `EMBEDDED_COMPONENTS` controls.
 * They are children of {@link BASE_DOCUMENT_TRANSLOCO_SCOPE} rather than scopes of their own, because
 * the whole graph is edited under `BASE_DOCUMENT_ROUTES` and one scope registration has to cover all of
 * it — the embedded route branches add none. Separate roots rather than one shared with the document:
 * both ports have a `name`, a `type` and a `description` of their own, which would otherwise collide
 * with each other and with the document's own labels in a single namespace.
 */
export const DOCUMENT_INPUT_PORT_I18N_SCOPE = `${BASE_DOCUMENT_TRANSLOCO_SCOPE}.document_input_port`;
export const DOCUMENT_OUTPUT_PORT_I18N_SCOPE = `${BASE_DOCUMENT_TRANSLOCO_SCOPE}.document_output_port`;
