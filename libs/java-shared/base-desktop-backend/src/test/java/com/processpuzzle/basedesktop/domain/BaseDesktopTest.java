package com.processpuzzle.basedesktop.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseDesktopTest {

    @Test
    void constructor_shouldDefaultPanelsToEmptyList() {
        BaseDesktop desktop = new BaseDesktop("My Workspace");

        assertEquals("My Workspace", desktop.getTitle());
        assertTrue(desktop.getPanels().isEmpty());
        assertEquals(0, desktop.panelCount());
    }

    @Test
    void panelCount_shouldReportTheNumberOfPanelsProvided() {
        BaseDesktop desktop = new BaseDesktop("My Workspace", List.of("nav", "editor", "console"));

        assertEquals(3, desktop.panelCount());
    }
}
