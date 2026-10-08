package com.processpuzzle.shared.event;

/**
 * What happened to the subject of a {@link PlatformEvent}. The first three are base-entity's
 * lifecycle; {@link #STATE_CHANGED} is base-state's, and is the only one that carries a
 * {@link PlatformEvent#state()}.
 */
public enum PlatformEventAction {
    CREATED, UPDATED, DELETED, STATE_CHANGED
}
