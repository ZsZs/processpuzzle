import { Component } from '@angular/core';
import { provideTranslocoScope, TranslocoDirective, TranslocoPipe } from '@jsverse/transloco';
import { FormGroup } from '@angular/forms';
import { MatLabel } from '@angular/material/input';

@Component({
  selector: 'test-transloco.component',
  imports: [TranslocoDirective, TranslocoPipe, MatLabel],
  templateUrl: './test-transloco.component.html',
  styleUrl: './test-transloco.component.css',
  providers: [provideTranslocoScope({ scope: 'auth' })],
})
export class TestTranslocoComponent {
  testForm!: FormGroup;
}
