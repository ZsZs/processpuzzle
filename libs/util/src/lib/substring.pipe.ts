import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'substring' })
export class SubstringPipe implements PipeTransform {
  transform(text: any, startFrom = 0, length?: number): any {
    return text.substring(startFrom, length);
  }
}
