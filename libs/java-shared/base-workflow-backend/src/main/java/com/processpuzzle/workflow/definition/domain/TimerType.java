package com.processpuzzle.workflow.definition.domain;

/**
 * How a {@link TimerDefinition}'s expression is read.
 *
 * <ul>
 *   <li>DURATION — fires once, the given time after the timer is reached: {@code PT2H}, {@code P3D}.</li>
 *   <li>DATE — fires once, at the given moment: {@code 2026-10-10T08:00:00Z}, or a date.</li>
 *   <li>CYCLE — fires repeatedly: {@code R3/PT1H}, {@code R/PT1H}, {@code R3/2026-10-10T08:00:00Z/P1D}.</li>
 * </ul>
 */
public enum TimerType {
    DURATION, DATE, CYCLE
}
