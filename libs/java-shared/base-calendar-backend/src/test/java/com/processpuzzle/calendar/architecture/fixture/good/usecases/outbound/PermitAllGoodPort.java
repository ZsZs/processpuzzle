package com.processpuzzle.calendar.architecture.fixture.good.usecases.outbound;

public class PermitAllGoodPort implements GoodPort {
    @Override
    public void send(String id) {
        // permits by default
    }
}
