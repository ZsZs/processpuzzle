import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  standalone: true,
  selector: '[flexBoxHost]',
})
export class FlexBoxHostDirective {
  constructor(public viewContainerRef: ViewContainerRef) {}
}
