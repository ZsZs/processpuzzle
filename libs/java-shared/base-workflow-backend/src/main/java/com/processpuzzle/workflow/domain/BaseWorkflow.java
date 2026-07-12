package com.processpuzzle.workflow.domain;

import java.util.Collections;
import java.util.List;

public class BaseWorkflow {
    private final String name;
    private final List<String> steps;

    public BaseWorkflow(String name) {
        this(name, Collections.emptyList());
    }

    public BaseWorkflow(String name, List<String> steps) {
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
