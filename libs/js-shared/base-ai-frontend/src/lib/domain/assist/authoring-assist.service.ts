import { HttpClient, HttpContext } from '@angular/common/http';
import { computed, inject, Injectable, Signal, signal } from '@angular/core';
import type { AiAssistClient, AiAssistRequest, AiAssistResult } from '@processpuzzle/base-entity';
import { EXPECTED_ERROR_STATUSES, RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';

/**
 * What the forms' AI assist button talks to: base-ai's authoring assist endpoints. At construction it asks which tasks the
 * user can use, once; a stack without the assistant, an organization without an AI subscription and a user who may not
 * design all come back as no tasks, or as the statuses below, and the buttons stay hidden. Those statuses are expected
 * answers here, so the central error interceptor does not report them.
 */
@Injectable({ providedIn: 'root' })
export class AuthoringAssistService implements AiAssistClient {
  /** The statuses of a probe that simply means "not available here": not signed in, not allowed, no assistant. */
  private static readonly UNAVAILABLE = [401, 403, 404];

  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'AI_SERVICE_ROOT');
  private readonly tasks = signal<readonly string[]>([]);

  constructor() {
    this.loadTasks();
  }

  isAvailable(task: string): Signal<boolean> {
    return computed(() => this.tasks().includes(task));
  }

  run(task: string, request: AiAssistRequest): Promise<AiAssistResult> {
    return firstValueFrom(this.http.post<AiAssistResult>(`${this.root}/ai/assist/${encodeURIComponent(task)}`, request));
  }

  private loadTasks(): void {
    if (!this.root) {
      return;
    }
    const context = new HttpContext().set(EXPECTED_ERROR_STATUSES, AuthoringAssistService.UNAVAILABLE);
    this.http.get<{ tasks: string[] }>(`${this.root}/ai/assist`, { context }).subscribe({
      next: (answer) => this.tasks.set(answer.tasks ?? []),
      error: () => this.tasks.set([]),
    });
  }
}
