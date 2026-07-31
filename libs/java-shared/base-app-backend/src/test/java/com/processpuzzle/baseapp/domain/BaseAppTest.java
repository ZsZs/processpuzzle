package com.processpuzzle.baseapp.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAppTest {

    @Test
    void constructor_shouldDefaultPanelsToEmptyList() {
        BaseApp app = new BaseApp("My Workspace");

        assertEquals("My Workspace", app.getTitle());
        assertTrue(app.getPanels().isEmpty());
        assertEquals(0, app.panelCount());
    }

    @Test
    void panelCount_shouldReportTheNumberOfPanelsProvided() {
        BaseApp app = new BaseApp("My Workspace", List.of("nav", "editor", "console"));

        assertEquals(3, app.panelCount());
    }
}
