package com.processpuzzle.calendar.architecture.fixture.good.adapters.inbound;

import com.processpuzzle.calendar.architecture.fixture.good.usecases.inbound.GoodUseCase;
import org.springframework.http.HttpStatus;

public class GoodEndpoint {
    private final GoodUseCase useCase;

    public GoodEndpoint(GoodUseCase useCase) {
        this.useCase = useCase;
    }

    public HttpStatus handle(String id) {
        useCase.run(id);
        return HttpStatus.OK;
    }
}
