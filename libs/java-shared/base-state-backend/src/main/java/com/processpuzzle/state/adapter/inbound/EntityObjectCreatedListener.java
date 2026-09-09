package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.baseentity.instances.domain.event.EntityObjectCreatedEvent;
import com.processpuzzle.state.usecase.StartStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts an object's state machine when base-entity reports it created. The other half of
 * base-state being the sole writer of the state attribute: if the attribute could only ever be
 * written by a transition, a freshly created object would sit outside its own machine until
 * something happened to it.
 *
 * <p>{@code @TransactionalEventListener} — after commit, following {@code
 * TaskCompletionStateTriggerListener}. The creation is a fact by the time this runs, so a state
 * that cannot be written (no gateway wired, the object already deleted, a concurrent update) is
 * logged and dropped rather than allowed to roll back a creation base-state is only observing.
 *
 * <p><b>{@code REQUIRES_NEW}, not the default.</b> After commit the creating transaction is
 * completed but still bound to the thread, so a {@code REQUIRED} write would join it as a
 * participating transaction and never commit — it would flush into the shared persistence context,
 * be visible to whatever still holds the object, and then be discarded. The symptom is an initial
 * state that appears in the creating response and is gone on the next read. The write needs a
 * transaction of its own, and so do any listeners of the {@code EntityObjectStateChangedEvent} this
 * publishes.
 *
 * <p><b>Created only, deliberately.</b> Writing the state attribute is itself an update of the
 * object, so a listener on {@code EntityObjectUpdatedEvent} that wrote would re-enter itself. Two
 * things keep that from happening: base-state's write goes through {@code EntityObjectAccess},
 * which bypasses the update use case and publishes nothing, and this module subscribes to
 * creations only. Either alone would do; both is cheap, and the loop is the kind that only shows up
 * in production.
 */
@Component
class EntityObjectCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(EntityObjectCreatedListener.class);

    private final StartStateMachine startStateMachine;

    EntityObjectCreatedListener(StartStateMachine startStateMachine) {
        this.startStateMachine = startStateMachine;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(EntityObjectCreatedEvent event) {
        try {
            startStateMachine.execute(
                    event.orgKey(), event.entityDefinitionCode(), event.objectId(),
                    event.payload(), event.version())
                .ifPresent(stateKey -> log.debug(
                        "Started the state machine for {}/{} at '{}'.",
                        event.entityDefinitionCode(), event.objectId(), stateKey));
        } catch (RuntimeException e) {
            log.error("Could not write the initial state onto the newly created {}/{}. The object exists "
                            + "but is not in its state machine, and no transition will be available on it "
                            + "until its state attribute is set.",
                    event.entityDefinitionCode(), event.objectId(), e);
        }
    }
}
