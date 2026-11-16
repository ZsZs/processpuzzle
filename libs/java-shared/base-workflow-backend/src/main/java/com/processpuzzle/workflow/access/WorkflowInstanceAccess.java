package com.processpuzzle.workflow.access;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to the existence and status of workflow instances, for a module that attaches something to an
 * instance without owning it — base-chat, through the host application's adapter.
 *
 * <p>Every method is scoped to the organization: an instance another organization owns is not found, exactly like
 * one that does not exist.
 */
public interface WorkflowInstanceAccess {

    /**
     * @param workflowId the workflow definition the instance was started from; an instance of another definition
     *                   is not found
     * @return the instance, or empty when there is none of that definition in {@code orgKey}
     */
    Optional<WorkflowInstanceView> find(String orgKey, String workflowId, UUID instanceId);

    /** The statuses an instance can have, as the names {@link WorkflowInstanceView#status()} carries. */
    List<String> statuses();
}
