package com.processpuzzle.calculation.domain;

/**
 * What started a run: a request ({@code MANUAL}), a created or updated object ({@code ON_CHANGE}), an object
 * entering the trigger's state ({@code STATE}) or the freeze state ({@code FREEZE_STATE}), which recomputes once
 * more and freezes the result.
 */
public enum RunTrigger {
    MANUAL, ON_CHANGE, STATE, FREEZE_STATE
}
