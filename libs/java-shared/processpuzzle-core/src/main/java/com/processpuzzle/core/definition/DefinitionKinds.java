package com.processpuzzle.core.definition;

/**
 * The definition kinds a bundle or a release may carry, and the order they are imported and released
 * in. A later kind may validate against an earlier one — a state machine names an entity attribute, a
 * rule's context names an entity — so the order is part of the contract, not a convenience.
 *
 * <p>{@link #MODULE} is imported with the app that mounts it, ahead of it, and released as it is imported. {@link #EVENT}
 * and the workflow catalogue — {@link #ARTIFACT}, {@link #ROLE}, {@link #TOOL}, {@link #TASK} — are not imported on their own: a
 * workflow bundle carries its catalogue. They are released before the workflows that use them, a task after the roles,
 * artifacts and tools it names.
 *
 * <p>{@link #TRANSLATION} and {@link #ASSET} are not definitions but what a starter's modules and apps bring along:
 * the translated texts of a module or an app, and the files of a module or an app, all after the apps (the texts last,
 * because an app's texts need the app to exist). They are only imported — a release carries them with the module or
 * app that owns them.
 *
 * <p>A {@link #CALCULATION} reads and writes entity attributes, so it comes after the entities; it is released
 * after the rules, which it does not name, and before the workflows, whose states may trigger it.
 */
public final class DefinitionKinds {

    public static final String ENTITY = "entity";
    public static final String STATE = "state";
    public static final String RULE = "rule";
    public static final String CALCULATION = "calculation";
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
    public static final String TRANSLATION = "translation";
    public static final String ASSET = "asset";

    public static final int ENTITY_ORDER = 10;
    public static final int STATE_ORDER = 20;
    public static final int RULE_ORDER = 30;
    public static final int CALCULATION_ORDER = 35;
    public static final int WIDGET_ORDER = 40;
    public static final int DOCUMENT_ORDER = 50;
    public static final int EVENT_ORDER = 52;
    public static final int ARTIFACT_ORDER = 54;
    public static final int ROLE_ORDER = 55;
    public static final int TOOL_ORDER = 56;
    public static final int TASK_ORDER = 58;
    public static final int WORKFLOW_ORDER = 60;
    public static final int MODULE_ORDER = 65;
    public static final int TRANSLATION_ORDER = 76;
    public static final int APP_ORDER = 70;
    public static final int ASSET_ORDER = 75;

    private DefinitionKinds() {
    }
}
