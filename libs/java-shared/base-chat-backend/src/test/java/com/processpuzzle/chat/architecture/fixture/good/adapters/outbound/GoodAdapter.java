package com.processpuzzle.chat.architecture.fixture.good.adapters.outbound;

import com.processpuzzle.chat.architecture.fixture.good.usecases.outbound.GoodPort;

public class GoodAdapter implements GoodPort {
    @Override
    public void send(String id) {
        // delivered elsewhere
    }
}
