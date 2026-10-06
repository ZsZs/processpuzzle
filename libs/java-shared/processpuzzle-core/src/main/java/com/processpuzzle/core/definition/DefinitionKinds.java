package com.processpuzzle.core.definition;

/**
 * The definition kinds a bundle may carry, and the order they are imported in. A later kind may
 * validate against an earlier one — a state machine names an entity attribute, a rule's context
 * names an entity — so the order is part of the contract, not a convenience.
 */
public final class DefinitionKinds {

    public static final String ENTITY = "entity";
    public static final String STATE = "state";
    public static final String RULE = "rule";
    public static final String WIDGET = "widget";
    public static final String DOCUMENT = "document";
    public static final String WORKFLOW = "workflow";
    public static final String APP = "app";

    public static final int ENTITY_ORDER = 10;
    public static final int STATE_ORDER = 20;
    public static final int RULE_ORDER = 30;
    public static final int WIDGET_ORDER = 40;
    public static final int DOCUMENT_ORDER = 50;
    public static final int WORKFLOW_ORDER = 60;
    public static final int APP_ORDER = 70;

    private DefinitionKinds() {
    }
}
