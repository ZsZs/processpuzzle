package com.processpuzzle.rule.domain;

import org.springframework.modulith.NamedInterface;

/**
 * How badly a failed rule should be taken. The one domain type other modules may reference — it is
 * part of {@link com.processpuzzle.rule.usecase.RuleViolation}'s signature, so a caller cannot read
 * an evaluation result without it. Everything else in this package stays internal, hence the
 * type-level named interface rather than one on the package.
 */
@NamedInterface("domain")
public enum Severity {
    ERROR,
    WARNING,
    INFO
}
