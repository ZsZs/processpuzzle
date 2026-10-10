package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.usecases.inbound.SyncRoleDirectory;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.StartTimerScheduleRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceCounterRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.i18n.WorkflowTranslationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Deletes this feature's rows of an erased tenant — its running and finished instances with their
 * tasks, artifacts, events, start-timer schedules and instance counter, then its definitions and
 * translations — and removes the tenant's roles from the identity provider once that has committed.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, for the reason base-app's cleaner gives: these deletes join the
 * erasing transaction, so a tenant is either erased in every feature or in none. An
 * {@code AFTER_COMMIT} listener's writes would be discarded without an error.
 *
 * <p><b>Bulk statements, runtime before design time.</b> No table here has a foreign key or a JPA
 * association — instances name their definitions by id, and every table carries its own
 * {@code org_key} — so each is one {@code delete … where orgKey}. The order is the logical one,
 * child before parent, so that the order still holds if a constraint is ever added. Because it is one
 * transaction, the timer sweep and the after-commit listeners never see an instance whose definition
 * has gone: they see either all of the tenant or none of it.
 *
 * <p><b>The role directory only after commit.</b> Each role definition is projected into the identity
 * provider as a realm role or an organization group, and kept in step by
 * {@code RoleDirectorySyncListener} on {@code RoleDefinitionDeletedEvent} — which a bulk delete does
 * not publish. So the ids are read before the rows go, and each is handed to
 * {@link SyncRoleDirectory#remove} once the erasure has committed; that is the same removal path, and
 * it keeps the reserved-name skip. A rolled-back erasure leaves the directory as it was. Every removal
 * is best-effort: when the tenant is an Organization, platform-admin deletes it and its groups right
 * afterwards anyway, so a failure here is logged and nothing more.
 *
 * <p>Named, because base-app, base-rule and the other features declare a {@code TenantDataCleaner} too,
 * and an application that assembles several features would otherwise have two beans of one name.
 */
@Component("workflowTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final EventInstanceRepository eventInstances;
    private final TaskInstanceRepository taskInstances;
    private final ArtifactInstanceRepository artifactInstances;
    private final WorkflowInstanceRepository workflowInstances;
    private final WorkflowInstanceCounterRepository instanceCounters;
    private final StartTimerScheduleRepository startTimerSchedules;
    private final WorkflowDiagramRepository diagrams;
    private final WorkflowRepository workflows;
    private final TaskDefinitionRepository taskDefinitions;
    private final ArtifactDefinitionRepository artifactDefinitions;
    private final RoleDefinitionRepository roleDefinitions;
    private final ToolDefinitionRepository toolDefinitions;
    private final WorkflowTranslationRepository translations;
    private final SyncRoleDirectory roleDirectory;

    public TenantDataCleaner(EventInstanceRepository eventInstances, TaskInstanceRepository taskInstances,
                             ArtifactInstanceRepository artifactInstances,
                             WorkflowInstanceRepository workflowInstances,
                             WorkflowInstanceCounterRepository instanceCounters,
                             StartTimerScheduleRepository startTimerSchedules, WorkflowDiagramRepository diagrams,
                             WorkflowRepository workflows, TaskDefinitionRepository taskDefinitions,
                             ArtifactDefinitionRepository artifactDefinitions,
                             RoleDefinitionRepository roleDefinitions, ToolDefinitionRepository toolDefinitions,
                             WorkflowTranslationRepository translations, SyncRoleDirectory roleDirectory) {
        this.eventInstances = eventInstances;
        this.taskInstances = taskInstances;
        this.artifactInstances = artifactInstances;
        this.workflowInstances = workflowInstances;
        this.instanceCounters = instanceCounters;
        this.startTimerSchedules = startTimerSchedules;
        this.diagrams = diagrams;
        this.workflows = workflows;
        this.taskDefinitions = taskDefinitions;
        this.artifactDefinitions = artifactDefinitions;
        this.roleDefinitions = roleDefinitions;
        this.toolDefinitions = toolDefinitions;
        this.translations = translations;
        this.roleDirectory = roleDirectory;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();
        List<String> roleIds = roleDefinitions.findIdsByOrgKey(orgKey);

        int erasedEvents = eventInstances.eraseByOrgKey(orgKey);
        int erasedTasks = taskInstances.eraseByOrgKey(orgKey);
        int erasedArtifacts = artifactInstances.eraseByOrgKey(orgKey);
        int erasedInstances = workflowInstances.eraseByOrgKey(orgKey);
        instanceCounters.eraseByOrgKey(orgKey);
        int erasedSchedules = startTimerSchedules.eraseByOrgKey(orgKey);
        diagrams.eraseByOrgKey(orgKey);
        int erasedWorkflows = workflows.eraseByOrgKey(orgKey);
        int erasedDefinitions = taskDefinitions.eraseByOrgKey(orgKey) + artifactDefinitions.eraseByOrgKey(orgKey)
                + roleDefinitions.eraseByOrgKey(orgKey) + toolDefinitions.eraseByOrgKey(orgKey);
        int erasedTranslations = translations.eraseByOrgKey(orgKey);

        removeRolesAfterCommit(orgKey, roleIds);
        LOG.info("Organization '{}' erased: {} workflow instance(s) with {} task(s), {} artifact(s) and {} "
                        + "event(s), {} start-timer schedule(s), {} workflow(s), {} role/task/artifact/tool "
                        + "definition(s) and {} translation bundle(s) deleted.", orgKey, erasedInstances,
                erasedTasks, erasedArtifacts, erasedEvents, erasedSchedules, erasedWorkflows, erasedDefinitions,
                erasedTranslations);
    }

    private void removeRolesAfterCommit(String orgKey, List<String> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                roleIds.forEach(roleId -> removeRole(orgKey, roleId));
            }
        });
    }

    /**
     * {@link SyncRoleDirectory#remove} already skips reserved names and logs an unreachable identity
     * provider; anything else it lets through is caught here, because after commit there is nobody left
     * to report it to, and one failed role must not keep the others in the directory.
     */
    private void removeRole(String orgKey, String roleId) {
        try {
            roleDirectory.remove(orgKey, roleId);
        } catch (RuntimeException ex) {
            LOG.warn("Could not remove role '{}' of erased organization '{}' from the identity provider. {}",
                    roleId, orgKey, ex.getMessage());
        }
    }
}
