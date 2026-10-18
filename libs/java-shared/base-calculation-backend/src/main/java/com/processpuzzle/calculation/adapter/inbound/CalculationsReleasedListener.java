package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.usecase.CalculationsReleased;
import com.processpuzzle.calculation.usecase.CatchUpFreezes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Catches up the freezes of what was just released, once the release has committed — the runs read the released
 * rows. Outside a transaction too: a release a caller runs without one still catches up.
 */
@Component
class CalculationsReleasedListener {

    private static final Logger LOG = LoggerFactory.getLogger(CalculationsReleasedListener.class);

    private final CatchUpFreezes catchUpFreezes;

    CalculationsReleasedListener(CatchUpFreezes catchUpFreezes) {
        this.catchUpFreezes = catchUpFreezes;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(CalculationsReleased released) {
        int frozen = catchUpFreezes.execute(released.orgKey(), released.calculationIds());
        if (frozen > 0) {
            LOG.info("Froze {} result(s) of calculations released in '{}' whose subjects were already in their freeze state",
                    frozen, released.orgKey());
        }
    }
}
