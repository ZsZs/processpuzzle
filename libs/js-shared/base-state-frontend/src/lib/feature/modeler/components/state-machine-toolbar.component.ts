import { Component, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'pp-state-machine-toolbar',
  standalone: true,
  template: `
    <div class="toolbar">
      <button (click)="addState.emit()">Add State</button>
      <button (click)="addTransition.emit()">Add Transition</button>
      <button (click)="zoomIn.emit()">Zoom In</button>
      <button (click)="zoomOut.emit()">Zoom Out</button>
    </div>
  `,
  styles: [
    `
      .toolbar {
        display: flex;
        gap: 8px;
        padding: 8px;
        background: #eee;
        border-bottom: 1px solid #ccc;
      }
    `,
  ],
})
export class StateMachineToolbarComponent {
  @Output() addState = new EventEmitter<void>();
  @Output() addTransition = new EventEmitter<void>();
  @Output() zoomIn = new EventEmitter<void>();
  @Output() zoomOut = new EventEmitter<void>();
}
