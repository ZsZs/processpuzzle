package com.processpuzzle.event.usecase;

/**
 * The state change a SYSTEM event definition stands for: an object of {@code subjectType} entering {@code state} —
 * any state when {@code state} is null. What a consumer outside this module asks of the catalog, without reading the
 * definition itself.
 */
public record EventStateChange(String subjectType, String state) {}
