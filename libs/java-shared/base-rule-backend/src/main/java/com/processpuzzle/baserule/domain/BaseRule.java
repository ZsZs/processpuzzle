package com.processpuzzle.baserule.domain;

public class BaseRule {
    private final String name;
    private final int priority;

    public BaseRule(String name) {
        this(name, 0);
    }

    public BaseRule(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public String describe() {
        return name + " (priority " + priority + ")";
    }
}
