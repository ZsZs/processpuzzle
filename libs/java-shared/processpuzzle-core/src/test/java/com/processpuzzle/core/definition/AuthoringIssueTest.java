package com.processpuzzle.core.definition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthoringIssueTest {

    @Test
    void anErrorCarriesItsPathAndText() {
        AuthoringIssue issue = AuthoringIssue.error("attributes[2].valueKind", "value kind is required");

        assertThat(issue.severity()).isEqualTo(AuthoringIssue.Severity.ERROR);
        assertThat(issue.path()).isEqualTo("attributes[2].valueKind");
        assertThat(issue.text()).isEqualTo("value kind is required");
    }

    @Test
    void aWarningIsNotAnError() {
        AuthoringIssue issue = new AuthoringIssue(AuthoringIssue.Severity.WARNING, "", "saved, but check the label");

        assertThat(issue.severity()).isEqualTo(AuthoringIssue.Severity.WARNING);
        assertThat(issue).isNotEqualTo(AuthoringIssue.error("", "saved, but check the label"));
    }
}
