/**
 * The routing algorithms a transition may be drawn with, and the rule for which of them an edge is
 * *currently* drawn with.
 *
 * These are ng-diagram's three built-in routings, listed in the order the menu offers them: the right-angle
 * one first, because a state machine is a technical diagram and that is what one normally wants, then the
 * two alternatives. The choice is per edge and is persisted by `EdgeLayout.routing`, which the contract
 * declares opaque to the backend — it is presentation, exactly like a node's position.
 *
 * Free of Angular, and apart from the menu that shows them: which routings exist is a fact about the
 * diagram, and {@link activeEdgeRouting} is a rule worth being able to test on its own.
 */

/** A routing a user may pick. ng-diagram's `EdgeRoutingName` also admits custom ones; none is registered. */
export type EdgeRoutingChoice = 'orthogonal' | 'polyline' | 'bezier';

/** In menu order. */
export const EDGE_ROUTING_CHOICES: readonly EdgeRoutingChoice[] = ['orthogonal', 'polyline', 'bezier'];

/**
 * What an edge that names no routing of its own is drawn with.
 *
 * Stated here and handed to ng-diagram as `edgeRouting.defaultRouting` by
 * {@link StateMachineCanvasComponent}, rather than relying on the library's own default: the menu has to
 * tick the item an edge is *actually* drawn with, and an unset `routing` is the normal case — most edges
 * have never been through this menu.
 */
export const DEFAULT_EDGE_ROUTING: EdgeRoutingChoice = 'orthogonal';

/**
 * Which menu item to tick for an edge whose `routing` is `routing`.
 *
 * An unset routing reads as {@link DEFAULT_EDGE_ROUTING}, and so does a value this build does not offer:
 * a layout saved by a later version — or hand-edited — must not leave the menu with nothing ticked, and
 * the default is what ng-diagram will have drawn such an edge with anyway.
 */
export function activeEdgeRouting(routing?: string): EdgeRoutingChoice {
  return EDGE_ROUTING_CHOICES.find((choice) => choice === routing) ?? DEFAULT_EDGE_ROUTING;
}
