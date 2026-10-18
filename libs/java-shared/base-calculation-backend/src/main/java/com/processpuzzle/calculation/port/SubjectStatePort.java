package com.processpuzzle.calculation.port;

import java.util.List;

/**
 * Which objects are in a state — for the freeze a calculation would have made had it been released when they
 * entered it. Optional: a host without state machines supplies none, and nothing is caught up.
 */
public interface SubjectStatePort {

    /** The ids of the objects of {@code entity} whose state is {@code state}; empty when no machine governs it. */
    List<String> objectsInState(String orgKey, String entity, String state);
}
