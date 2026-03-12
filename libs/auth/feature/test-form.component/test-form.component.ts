import { Component } from '@angular/core';
import { provideTranslocoScope } from '@jsverse/transloco';
import { form, FormField } from '@angular/forms/signals';
import { testFormData } from './test-form.data';

@Component({
  selector: 'pp-test-form',
  imports: [FormField],
  templateUrl: './test-form.component.html',
  styleUrl: './test-form.component.css',
  providers: [provideTranslocoScope({ scope: 'auth' })],
})
export class TestFormComponent {
  testForm = form(testFormData);
}
