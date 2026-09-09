/**
 * Domain events the definition module publishes as an organization's {@code RoleDefinition} catalog
 * changes. Exposed as the {@code event} named interface — following base-entity's
 * {@code instances.domain.event} — so another module may observe a role's lifecycle without being
 * allowed at {@code RoleDefinitionRepository} or the aggregate behind it.
 *
 * <p>Both events are published inside the writing transaction and are meant to be observed
 * <em>after</em> it commits, via {@code @TransactionalEventListener(phase = AFTER_COMMIT)}. An
 * observer therefore cannot veto the write it is reacting to: the catalog is the system of record
 * and everything downstream is a projection of it. The one observer in this library,
 * {@code RoleDirectorySyncListener}, projects the catalog into the identity provider's realm roles.
 *
 * <p>Only roles have events. Artifacts, tools and tasks are consumed by this module alone, so there
 * is nothing for an observer to keep in step; a role, by contrast, is what a task is performed by,
 * which makes it the one catalog entry that has to exist outside base-workflow as well.
 */
@NamedInterface("event")
package com.processpuzzle.workflow.definition.domain.event;

import org.springframework.modulith.NamedInterface;
