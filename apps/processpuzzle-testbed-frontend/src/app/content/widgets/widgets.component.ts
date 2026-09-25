import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatTabLink, MatTabNav, MatTabNavPanel } from '@angular/material/tabs';

@Component({
  selector: 'app-widgets',
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet, MatTabNav, MatTabNavPanel, MatTabLink],
  templateUrl: './widgets.component.html',
})
export class WidgetsComponent {}
