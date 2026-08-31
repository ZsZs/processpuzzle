/**
 * Domain events published via Spring's {@code ApplicationEventPublisher} as workflow instances
 * progress. Published as plain events (not Spring Modulith {@code @Externalized}) — externalizing
 * to a broker is an application-level concern for the host (processpuzzle-backend) to configure,
 * not something this feature library should assume.
 *
 * <p><b>Known gap:</b> there is no {@code base-workflow-events.yaml} contract anywhere in the repo
 * yet (checked at the time this module was implemented). These event classes are this module's own
 * best-effort design of what base-app/base-entity/a UI would plausibly need to hear about, not a
 * negotiated contract — expect to revisit their shape once a shared event schema exists. Symmetrically,
 * this module listens for nothing from base-state yet (it doesn't publish an instance-level event
 * either — see {@code ArtifactInstance}'s Javadoc), so {@code ArtifactInstance.currentState}
 * currently never gets refreshed after creation. Wiring that up is the natural next step once
 * base-state grows past its current scaffold.
 */
@org.springframework.modulith.NamedInterface("events")
package com.processpuzzle.workflow.execution.events;
