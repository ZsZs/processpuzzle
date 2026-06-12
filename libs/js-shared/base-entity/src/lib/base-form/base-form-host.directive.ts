import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  standalone: true,
  selector: '[baseFormHost]',
})
export class BaseFormHostDirective {
  constructor(public viewContainerRef: ViewContainerRef) {}
}
