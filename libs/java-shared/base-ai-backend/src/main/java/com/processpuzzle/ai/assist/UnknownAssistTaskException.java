package com.processpuzzle.ai.assist;

/** No participant implements the task a request named. */
public class UnknownAssistTaskException extends RuntimeException {

    public UnknownAssistTaskException(String task) {
        super("No assist task '%s'".formatted(task));
    }
}
