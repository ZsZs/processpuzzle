package com.processpuzzle.rule.adapter.inbound.dto;

import java.util.List;

public class ImportResult {

    private final int created;
    private final int updated;
    private final List<String> errors;

    public ImportResult(int created, int updated, List<String> errors) {
        this.created = created;
        this.updated = updated;
        this.errors = errors;
    }

    public int getCreated() {
        return created;
    }

    public int getUpdated() {
        return updated;
    }

    public List<String> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
