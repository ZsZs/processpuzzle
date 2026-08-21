import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class DiagramInteractionService {
  zoom = 1;

  zoomIn() {
    this.zoom = Math.min(2, this.zoom + 0.1);
  }

  zoomOut() {
    this.zoom = Math.max(0.5, this.zoom - 0.1);
  }
}
