package com.processpuzzle.calendar.architecture.fixture.good.usecases.inbound;

import com.processpuzzle.calendar.architecture.fixture.good.domain.GoodEntity;
import com.processpuzzle.calendar.architecture.fixture.good.usecases.outbound.GoodPort;

public class GoodUseCase {
    private final GoodPort port;

    public GoodUseCase(GoodPort port) {
        this.port = port;
    }

    public GoodEntity run(String id) {
        port.send(id);
        return new GoodEntity(id);
    }
}
