package com.processpuzzle.baseapp.domain;

import java.util.Collections;
import java.util.List;

public class BaseApp {
    private final String title;
    private final List<String> panels;

    public BaseApp(String title) {
        this(title, Collections.emptyList());
    }

    public BaseApp(String title, List<String> panels) {
        this.title = title;
        this.panels = List.copyOf(panels);
    }

    public String getTitle() {
        return title;
    }

    public List<String> getPanels() {
        return panels;
    }

    public int panelCount() {
        return panels.size();
    }
}
