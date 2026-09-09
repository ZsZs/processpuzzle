/**
 * A key/value map the generic form can author.
 *
 * A step's `inputMapping` and `outputMapping`, an instance's `context` and a step result's
 * `toolResponse` are all `additionalProperties` in the contract — any JSON value — but the
 * `ADDITIONAL_PROPERTIES` control edits text, so the model narrows to what a user can actually type.
 * A value a backend put there survives a round trip regardless; it is simply shown as text.
 *
 * In a dependency-free module of its own, like `workflow-entity-names.ts`, because it is the one type
 * both layers share: the definition layer writes the mappings, the execution layer reads what they
 * produced. Neither layer has to import the other to name it.
 */
export type PropertyMap = Record<string, string>;
