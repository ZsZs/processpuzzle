package com.processpuzzle.workflow.definition.adapters.inbound.release;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseState;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Puts each draft's release state on the models an endpoint answers with, as the designer's lists and forms show it.
 * Only drafts have one: a released row is what a draft is compared with.
 */
public final class ReleaseStates {

    private ReleaseStates() {
    }

    /**
     * @param models the models answered, in the channel they were read from
     * @return the same models
     */
    public static <M> List<M> stamp(Channel channel, ReleaseParticipant participant, String orgKey, List<M> models,
                                    Function<M, String> keyOf,
                                    BiConsumer<M, com.processpuzzle.workflow.model.ReleaseState> setter) {
        if (channel != Channel.DRAFT || models.isEmpty()) {
            return models;
        }
        Map<String, ReleaseState> states = ReleaseState.ofDrafts(participant, orgKey);
        for (M model : models) {
            ReleaseState state = states.get(keyOf.apply(model));
            setter.accept(model, state == null ? null : com.processpuzzle.workflow.model.ReleaseState.fromValue(state.name()));
        }
        return models;
    }

    /** {@link #stamp(Channel, ReleaseParticipant, String, List, Function, BiConsumer)} for one model. */
    public static <M> M stampOne(Channel channel, ReleaseParticipant participant, String orgKey, M model,
                                 Function<M, String> keyOf,
                                 BiConsumer<M, com.processpuzzle.workflow.model.ReleaseState> setter) {
        stamp(channel, participant, orgKey, List.of(model), keyOf, setter);
        return model;
    }
}
