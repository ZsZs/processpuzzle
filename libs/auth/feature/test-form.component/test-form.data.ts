import { signal } from '@angular/core';

export interface TestFormData {
  name: string;
  email: string;
}

export const testFormData = signal<TestFormData>({
  name: '',
  email: '',
});
