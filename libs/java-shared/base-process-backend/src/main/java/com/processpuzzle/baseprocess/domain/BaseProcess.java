package com.processpuzzle.baseprocess.domain;

import java.util.Collections;
import java.util.List;

public class BaseProcess {
    private final String name;
    private final List<String> steps;

    public BaseProcess(String name) {
        this(name, Collections.emptyList());
    }

    public BaseProcess(String name, List<String> steps) {
        this.name = name;
        this.steps = List.copyOf(steps);
    }

    public String getName() {
        return name;
    }

    public List<String> getSteps() {
        return steps;
    }

    public int stepCount() {
        return steps.size();
    }
}
