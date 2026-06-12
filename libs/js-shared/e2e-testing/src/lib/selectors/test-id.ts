/** "Test Entity Component" -> "testEntityComponent" */
export function toTestId(entityName: string): string {
  return entityName
    .split(' ')
    .map((word, i) => (i === 0 ? word.charAt(0).toLowerCase() + word.slice(1) : word.charAt(0).toUpperCase() + word.slice(1)))
    .join('');
}

export function formControlTestId(entityName: string, attrName: string): string {
  return `${toTestId(entityName)}-${attrName}`;
}
