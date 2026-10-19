package com.processpuzzle.workflow.definition.usecases.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogStateChangeTest {

    @Test
    void reachesTheNamedStateOfItsSubjectOnly() {
        CatalogStateChange cancelled = new CatalogStateChange("order", "CANCELLED");

        assertThat(cancelled.reaches("Order", "cancelled")).isTrue();
        assertThat(cancelled.reaches("order", "DELIVERED")).isFalse();
        assertThat(cancelled.reaches("invoice", "CANCELLED")).isFalse();
    }

    // No state named is any change of the subject.
    @Test
    void reachesEveryStateWhenItNamesNone() {
        assertThat(new CatalogStateChange("order", null).reaches("order", "SHIPPED")).isTrue();
        assertThat(new CatalogStateChange("order", " ").reaches("order", "SHIPPED")).isTrue();
    }

    @Test
    void reachesNothingWithoutASubject() {
        assertThat(new CatalogStateChange(null, "CANCELLED").reaches("order", "CANCELLED")).isFalse();
    }

    // A catalog adapter that predates state changes names none, so a catch of it handles no transition.
    @Test
    void anEventCatalogNamesNoStateChangeByDefault() {
        assertThat(new PermitAllEventCatalogPort().stateChangeOf("org-1", "OrderCancelledEvent")).isEmpty();
    }
}
