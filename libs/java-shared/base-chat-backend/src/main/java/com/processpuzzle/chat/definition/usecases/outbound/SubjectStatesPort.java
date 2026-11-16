package com.processpuzzle.chat.definition.usecases.outbound;

import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.core.definition.Channel;
import java.util.Optional;

/**
 * Outbound port to whatever knows the states of a kind of subject — base-state for an entity. A STATE_BOUND chat
 * names states of its subject, and a release must refuse one that names a state the subject cannot be in: such a
 * chat would stay read-only for ever, which nobody would notice until it was used.
 *
 * <p>Asked with a {@link Channel} because a designer's check is about the drafts, which is what is being released,
 * while the running application only knows what was released.
 */
public interface SubjectStatesPort {

    /**
     * @return the states of the subject kind, {@link SubjectStates#NONE} when nothing governs it, or empty when the
     *         host cannot say — which is not a refusal, so a definition is not held up by a host that cannot answer
     */
    Optional<SubjectStates> statesOf(String orgKey, SubjectType subjectType, String subjectDefinition, Channel channel);
}
