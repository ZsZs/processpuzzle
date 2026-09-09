/**
 * Domain events published by one feature and observed by others.
 *
 * <p>They live here, in the contracts module, rather than with their publisher, for the same reason
 * the REST schemas do: an event is a contract between modules, and a subscriber that has to compile
 * against the publisher's library to read one is coupled to the publisher's <em>implementation</em>,
 * not to its contract. base-app deletes its tenant-scoped rows in response to
 * {@link com.processpuzzle.shared.event.OrganizationDeletedEvent}, and that reaction is the whole of
 * what base-app needs to know about platform-admin — but while the event class was platform-admin's,
 * subscribing to it meant a Maven dependency on the entire module.
 *
 * <p>Both directions stay honest as a result: the publisher does not know its subscribers, and a
 * subscriber does not know the publisher. When these Modulith modules become separate services, an
 * event here is already the wire contract a broker would carry; only the transport changes.
 *
 * <p>Each event's Javadoc names the {@code @TransactionalEventListener} phase a subscriber must use
 * and why. Getting that wrong fails silently rather than loudly, so read it before subscribing.
 */
package com.processpuzzle.shared.event;
