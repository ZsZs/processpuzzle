package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The aggregate root of the definition layer: a SPEM-inspired process definition made up of
 * roles, work products, and tasks. All mutation of {@link RoleDefinition}, {@link
 * WorkProductDefinition}, and {@link TaskDefinition} — including the direct sub-resource endpoints
 * ({@code /processes/{id}/roles}, {@code /tasks}) — goes through this aggregate so that
 * {@link #version} stays a meaningful optimistic-lock guard over the whole definition, matching
 * "Use version to prevent lost updates" in base-workflow-api.yaml.
 *
 * <p>Composite key (orgKey, id) mirrors {@code RuleDefinition}'s convention: {@code id} is the
 * author-chosen business identifier (e.g. {@code "software-delivery"}), unique per tenant.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = {"orgKey", "id"})
@ToString(exclude = {"roles", "workProducts", "tasks"})
@Entity
@Table(name = "workflow_process_definition")
@IdClass(ProcessDefinitionKey.class)
public class ProcessDefinition extends com.processpuzzle.workflow.common.Auditable {

    @Id
    @Column(nullable = false)
    private String orgKey;

    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    @SuppressWarnings("java:S1450")
    private String description;

    /**
     * ID of another ProcessDefinition (same org) this one extends. Roles/work products/tasks are
     * inherited unless overridden by an identically-id'd child element (tasks additionally support
     * {@code override=true} for explicit replacement — see {@link TaskDefinition#isOverride()}).
     * Cycle safety is enforced by {@link ProcessDefinitionExtendsValidator}, mirroring
     * {@code RuleExtendsValidator} in base-rule-backend.
     */
    @SuppressWarnings("java:S1450")
    private String extendsProcessId;

    /** IDs of {@code ToolDefinition}s (same org) usable by this process's task steps. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> tools = new ArrayList<>();

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "role_order")
    @Builder.Default
    private List<RoleDefinition> roles = new ArrayList<>();

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "work_product_order")
    @Builder.Default
    private List<WorkProductDefinition> workProducts = new ArrayList<>();

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "task_order")
    @Builder.Default
    private List<TaskDefinition> tasks = new ArrayList<>();

    @Version
    private Long version;

    public void addRole(RoleDefinition role) {
        role.setProcess(this);
        this.roles.add(role);
    }

    public void addWorkProduct(WorkProductDefinition workProduct) {
        workProduct.setProcess(this);
        this.workProducts.add(workProduct);
    }

    public void addTask(TaskDefinition task) {
        task.setProcess(this);
        this.tasks.add(task);
    }

    public Optional<RoleDefinition> findRole(String roleId) {
        return roles.stream().filter(r -> r.getId().equals(roleId)).findFirst();
    }

    public Optional<WorkProductDefinition> findWorkProduct(String workProductId) {
        return workProducts.stream().filter(w -> w.getId().equals(workProductId)).findFirst();
    }

    public Optional<TaskDefinition> findTask(String taskId) {
        return tasks.stream().filter(t -> t.getId().equals(taskId)).findFirst();
    }

    /**
     * Replaces the entire roles/work products/tasks graph in-place, preserving this aggregate's
     * identity and {@link #version}. Used by the process-level PUT — the same "clear and rebuild
     * owned collections" convention {@code ReplaceEntityDefinitionUseCase} uses for
     * {@code BaseEntityAttribute}.
     */
    public void replaceContent(String name, String description, String extendsProcessId,
                                List<String> tools, List<RoleDefinition> newRoles,
                                List<WorkProductDefinition> newWorkProducts, List<TaskDefinition> newTasks) {
        this.name = name;
        this.description = description;
        this.extendsProcessId = extendsProcessId;
        this.tools = new ArrayList<>(tools);
        this.roles.clear();
        newRoles.forEach(this::addRole);
        this.workProducts.clear();
        newWorkProducts.forEach(this::addWorkProduct);
        this.tasks.clear();
        newTasks.forEach(this::addTask);
    }
}
