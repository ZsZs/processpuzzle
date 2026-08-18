/**
 * The process engine itself: {@code TaskActivationService} decides what runs next,
 * {@code ToolStepExecutor} runs tool-backed steps, and the rest are the one-use-case-per-operation
 * classes the inbound endpoints call — start/find/list/cancel process instance, list/find task
 * instance, assign/complete/skip task, list/find work product instance.
 */
package com.processpuzzle.workflow.execution.usecases.inbound;
