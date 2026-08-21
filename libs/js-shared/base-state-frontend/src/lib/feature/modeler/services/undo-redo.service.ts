import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class UndoRedoService {
  private history: any[] = [];
  private index = -1;

  push(snapshot: any) {
    this.history = this.history.slice(0, this.index + 1);
    this.history.push(snapshot);
    this.index = this.history.length - 1;
  }

  undo(): any | undefined {
    if (this.index > 0) {
      this.index--;
      return this.history[this.index];
    }
    return undefined;
  }

  redo(): any | undefined {
    if (this.index < this.history.length - 1) {
      this.index++;
      return this.history[this.index];
    }
    return undefined;
  }
}
