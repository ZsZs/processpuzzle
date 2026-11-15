import { computed, Injectable, signal, Signal } from '@angular/core';

/**
 * Knows which of the open entity forms hold edits that have not been saved.
 *
 * An embedded child's form and its owner's stay alive together, and the owner is what has to be saved even when
 * the edit was made in the child — so the status bar asks here rather than any one form.
 */
@Injectable({ providedIn: 'root' })
export class UnsavedChangesRegistry {
  private readonly sources = signal<Signal<boolean>[]>([]);

  /** True while any registered form has unsaved changes. */
  readonly hasUnsavedChanges = computed(() => this.sources().some((source) => source()));

  /** Registers a form's own "has unsaved changes" signal; the returned function unregisters it. */
  register(source: Signal<boolean>): () => void {
    this.sources.update((sources) => [...sources, source]);
    return () => this.sources.update((sources) => sources.filter((candidate) => candidate !== source));
  }
}
