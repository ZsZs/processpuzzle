import { inject, Injectable } from '@angular/core';
import { EntityRouteRegistry } from '@processpuzzle/base-entity';
import { ArtifactDefinition, ArtifactType } from '../definition/artifact-definition';
import { ArtifactInstance } from '../execution/workflow-instance';
import { ARTIFACT_DEFINITION_ENTITY_NAME, TOOL_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

/**
 * base-document's `DOCUMENT_ENTITY_NAME`, spelled out: this library does not depend on base-document, and
 * the name is the route contract between the two rather than an implementation detail of either.
 */
const DOCUMENT_ENTITY_NAME = 'Document';

/** base-document's Sign tab segment — the same route contract as {@link DOCUMENT_ENTITY_NAME}. */
const DOCUMENT_SIGN_SEGMENT = 'sign';

/** What a run is about — `WorkflowInstance.entityType` / `entityId` — when it is about anything. */
export interface RunSubject {
  readonly entityType?: string;
  readonly entityId?: string;
}

/**
 * Turns what the task dashboard shows — an artifact, a tool — into a URL the host actually mounts.
 *
 * **Every URL comes from `EntityRouteRegistry`**, which walks the router configuration, and never from a path
 * assembled here: the dashboard is mounted wherever the host puts it, and so is everything it links to. When
 * the registry knows no screen for a target the answer is `undefined` and the dashboard renders plain text —
 * a link nobody mounted is a 404 dressed up as a feature. That is also what a host that does not call
 * `provideEntityRouteRegistry()` gets: no links, nothing broken.
 *
 * An artifact links to the most specific thing that exists, in this order:
 *
 * 1. the **record** a run's instance points at — an `ENTITY` artifact's base-entity row (the entity named by
 *    `ArtifactDefinition.artifactTypeId`), or a `DOCUMENT` artifact's document;
 * 2. otherwise the **Artifact Definition** in the catalog — which is still the right place for an output
 *    nothing has produced yet, since that is where it says what it will be.
 *
 * A bound `DOCUMENT` artifact in a run that has a subject opens the document's **Sign** tab rather than its
 * details, with the subject as `?subjectType=&subjectId=` — {@link artifactQueryParams}. One document serves
 * every run, so the run's subject is the only thing that says whose contract the person at the dashboard
 * is signing.
 */
@Injectable({ providedIn: 'root' })
export class DashboardLinkResolver {
  private readonly routes = inject(EntityRouteRegistry);

  artifactLink(artifactDefinitionId: string, definition: ArtifactDefinition | undefined, instance: ArtifactInstance | undefined, subject?: RunSubject): string | undefined {
    return this.recordLink(definition, instance, subject) ?? this.routes.detailsPath(ARTIFACT_DEFINITION_ENTITY_NAME, artifactDefinitionId);
  }

  /** The query parameters {@link artifactLink} needs: the run's subject, for a document it links to the Sign tab of. */
  artifactQueryParams(definition: ArtifactDefinition | undefined, instance: ArtifactInstance | undefined, subject?: RunSubject): Record<string, string> | undefined {
    if (!this.signLink(definition, instance, subject)) return undefined;
    return { subjectType: subject!.entityType!, subjectId: subject!.entityId! };
  }

  toolLink(toolDefinitionId: string): string | undefined {
    return this.routes.detailsPath(TOOL_DEFINITION_ENTITY_NAME, toolDefinitionId);
  }

  private recordLink(definition: ArtifactDefinition | undefined, instance: ArtifactInstance | undefined, subject?: RunSubject): string | undefined {
    const signLink = this.signLink(definition, instance, subject);
    if (signLink) return signLink;
    const entityId = instance?.entityId;
    if (!entityId) return undefined;
    switch (instance.type ?? definition?.artifactType) {
      case ArtifactType.ENTITY: {
        const entityName = this.registeredEntityName(definition?.artifactTypeId);
        return entityName ? this.routes.detailsPath(entityName, entityId) : undefined;
      }
      case ArtifactType.DOCUMENT:
        return this.routes.detailsPath(DOCUMENT_ENTITY_NAME, entityId);
      default:
        return undefined;
    }
  }

  private signLink(definition: ArtifactDefinition | undefined, instance: ArtifactInstance | undefined, subject?: RunSubject): string | undefined {
    if (!instance?.entityId || !subject?.entityType || !subject.entityId) return undefined;
    if ((instance.type ?? definition?.artifactType) !== ArtifactType.DOCUMENT) return undefined;
    const base = this.routes.basePath(DOCUMENT_ENTITY_NAME);
    return base ? `${base}/${instance.entityId}/${DOCUMENT_SIGN_SEGMENT}` : undefined;
  }

  /**
   * The registered entity an `artifactTypeId` names. Matched loosely, because the two are written by
   * different people: the seed's `order` is the descriptor's `Order`, and a `special-order` would be a
   * `Special Order`.
   */
  private registeredEntityName(artifactTypeId: string | undefined): string | undefined {
    if (!artifactTypeId) return undefined;
    const wanted = normalized(artifactTypeId);
    return this.routes.registeredEntities().find((entityName) => normalized(entityName) === wanted);
  }
}

function normalized(name: string): string {
  return name.toLowerCase().replace(/[\s_-]+/g, '');
}
