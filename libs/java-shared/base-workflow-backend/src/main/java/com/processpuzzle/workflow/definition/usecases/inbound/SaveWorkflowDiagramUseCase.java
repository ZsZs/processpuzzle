package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates or replaces the diagram layout of one workflow — an upsert, unlike the
 * {@link CreateWorkflowUseCase} / {@link ReplaceWorkflowUseCase} pair next to it.
 *
 * <p>The asymmetry is deliberate. A second workflow with the same id is a genuine user error worth
 * conflicting on; saving a layout a second time is the normal case, and the modeler's "arrange"
 * gesture cannot reasonably be asked to know whether this workflow has ever been arranged before. The
 * client always supplies {@code workflowId}, so nothing is server-assigned and an upsert costs no
 * round trip and has no first-save race.
 *
 * <p>The workflow must exist: a layout for a workflow that does not exist has nothing to lay out, and
 * would be unreachable anyway. Individual {@code nodeId} and {@code edgeId} values are <em>not</em>
 * validated against it, though — {@link ReplaceWorkflowUseCase} is free to drop a task, a layout row
 * naming a node nothing renders any more is harmless (the modeler ignores what it cannot place, and
 * the next save prunes it), and rejecting it would make two independent, individually-valid saves fail
 * depending only on the order they arrive in.
 *
 * <p>Optimistic locking is the same shape as {@link ReplaceWorkflowUseCase}'s, and by the same
 * mechanism: a caller that supplies the {@code version} it last read is refused with a
 * {@link ConflictException} unless it still matches, so two modelers who both opened version 3 cannot
 * both save over it. Omitting it overwrites unconditionally — which is what the <em>first</em> save of a
 * workflow necessarily does, there being no stored version to have read.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class SaveWorkflowDiagramUseCase {

    private final WorkflowDiagramRepository repository;
    private final WorkflowRepository workflowRepository;

    /**
     * @param diagram the layout to persist; its {@code orgKey}/{@code workflowId} come from the request
     *                path, never from the request body
     * @return the persisted layout, and whether this call created it — which is what lets the endpoint
     *         answer {@code 201} the first time and {@code 200} thereafter
     */
    public Result save(WorkflowDiagram diagram) {
        String orgKey = diagram.getOrgKey();
        String workflowId = diagram.getWorkflowId();
        if (!workflowRepository.existsByOrgKeyAndId(orgKey, workflowId)) {
            throw new NotFoundException("No workflow definition with id '%s'".formatted(workflowId));
        }

        Optional<WorkflowDiagram> existing = repository.findByOrgKeyAndWorkflowId(orgKey, workflowId);
        if (existing.isEmpty()) {
            return new Result(repository.save(diagram), true);
        }

        WorkflowDiagram target = existing.get();
        if (diagram.getVersion() != null && !diagram.getVersion().equals(target.getVersion())) {
            throw new ConflictException(
                    "The diagram layout of workflow '%s' was modified concurrently — reload and retry".formatted(workflowId));
        }

        target.replaceLayout(diagram.getNodes(), diagram.getEdges(), diagram.getViewport());
        return new Result(repository.save(target), false);
    }

    /** @param created {@code true} when this save inserted the layout rather than replacing one. */
    public record Result(WorkflowDiagram diagram, boolean created) {
    }
}
