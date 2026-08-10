import { CommonModule } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { BaseEntityContainerComponent } from '@processpuzzle/base-entity';
import { Artifact } from '../domain/base-artifact';
import { createArtifactDescriptor } from '../domain/base-artifact.descriptors';
import { BaseArtifactStore } from '../domain/base-artifact.store';
import { ArtifactEditorComponent } from './artifact-editor/artifact-editor.component';

/**
 * List and (title/description/ports) Properties come from BaseEntityContainerComponent, exactly
 * as BaseRuleContainerComponent uses it — this component's only job is to stack the Content editor
 * underneath the generic Details route content on the same screen. Keeping the Properties save out
 * of here is deliberate: it belongs in BaseArtifactService.update, because the generic form is
 * rendered through a router-outlet and so has no binding back to this component. See the earlier
 * discussion of why a stacked single screen was chosen over a second tab: BaseEntityTabsComponent
 * only has List/Details, and a wiki page reads more naturally with its metadata and its content in
 * view together, the properties block collapsed by default since it's touched far less often than
 * the content below it.
 */
@Component({
  selector: 'pp-base-artifact-container',
  standalone: true,
  imports: [CommonModule, BaseEntityContainerComponent, ArtifactEditorComponent],
  template: `
    <base-entity-container [entityDescriptor]="entityDescriptor"></base-entity-container>

    @if (currentArtifact(); as artifact) {
      <pp-artifact-editor [artifactId]="artifact.id!" [blocks]="artifact.blocks" />
    }
  `,
})
export class BaseArtifactContainerComponent {
  protected readonly entityDescriptor = createArtifactDescriptor();

  private readonly store = inject(BaseArtifactStore);

  // BaseEntityContainerStore's own current-entity signal, typed back to Artifact — see
  // BaseRuleContainerComponent for the same cast, needed because the store is generic over
  // BaseEntity and doesn't know its own concrete type parameter at the injection site.
  protected readonly currentArtifact = computed(() => this.store.currentEntity() as Artifact | undefined);
}
