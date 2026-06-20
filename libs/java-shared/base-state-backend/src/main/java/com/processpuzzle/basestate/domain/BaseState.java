package com.processpuzzle.basestate.domain;

public class BaseState {
    private final String name;
    private final boolean isFinal;

    public BaseState(String name) {
        this(name, false);
    }

    public BaseState(String name, boolean isFinal) {
        this.name = name;
        this.isFinal = isFinal;
    }

    public String getName() {
        return name;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public String describe() {
        return isFinal ? name + " (final)" : name;
    }
}
