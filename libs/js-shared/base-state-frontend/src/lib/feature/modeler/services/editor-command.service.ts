import { Injectable } from '@angular/core';
import { StateMachine } from '../../domain/models/state-machine';

@Injectable({ providedIn: 'root' })
export class EditorCommandService {
  addState(sm: StateMachine) {
    // TODO: push new state, regenerate graph + layout
  }

  addTransition(sm: StateMachine) {
    // TODO: push new transition, regenerate graph + layout
  }

  // later: delete, rename, etc.
}
