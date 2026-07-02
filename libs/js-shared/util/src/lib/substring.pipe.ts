import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'substring' })
export class SubstringPipe implements PipeTransform {
  transform(text: string, startFrom = 0, length?: number): string {
    return text.substring(startFrom, length);
  }
}
