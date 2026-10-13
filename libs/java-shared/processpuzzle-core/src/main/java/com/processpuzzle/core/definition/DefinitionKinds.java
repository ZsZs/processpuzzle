package com.processpuzzle.core.definition;

/**
 * The definition kinds a bundle or a release may carry, and the order they are imported and released
 * in. A later kind may validate against an earlier one — a state machine names an entity attribute, a
 * rule's context names an entity — so the order is part of the contract, not a convenience.
 *
 * <p>{@link #MODULE} is released but not imported: no bundle carries modules yet. Nor are {@link #EVENT} and the
 * workflow catalogue — {@link #ARTIFACT}, {@link #ROLE}, {@link #TOOL}, {@link #TASK} — imported on their own: a
 * workflow bundle carries its catalogue. They are released before the workflows that use them, a task after the roles,
 * artifacts and tools it names.
 */
public final class DefinitionKinds {

    public static final String ENTITY = "entity";
    public static final String STATE = "state";
    public static final String RULE = "rule";
    public static final String WIDGET = "widget";
    public static final String DOCUMENT = "document";
    public static final String EVENT = "event";
    public static final String ARTIFACT = "artifact";
    public static final String ROLE = "role";
    public static final String TOOL = "tool";
    public static final String TASK = "task";
    public static final String WORKFLOW = "workflow";
    public static final String MODULE = "module";
    public static final String APP = "app";

    public static final int ENTITY_ORDER = 10;
    public static final int STATE_ORDER = 20;
    public static final int RULE_ORDER = 30;
    public static final int WIDGET_ORDER = 40;
    public static final int DOCUMENT_ORDER = 50;
    public static final int EVENT_ORDER = 52;
    public static final int ARTIFACT_ORDER = 54;
    public static final int ROLE_ORDER = 55;
    public static final int TOOL_ORDER = 56;
    public static final int TASK_ORDER = 58;
    public static final int WORKFLOW_ORDER = 60;
    public static final int MODULE_ORDER = 65;
    public static final int APP_ORDER = 70;

    private DefinitionKinds() {
    }
}
