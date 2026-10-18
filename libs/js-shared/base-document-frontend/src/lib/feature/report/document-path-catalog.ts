import { inject, Injectable } from '@angular/core';
import { EntityAttributeDefinition, EntityDefinition, EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { ValueFormat } from '@processpuzzle/widgets';
import { DocumentInputPort, PortType } from '../../domain/base-document';

/** One value an author can place: a port path, how it reads, and the format its value kind suggests. */
export interface DocumentFieldPath {
  path: string;
  label: string;
  format: ValueFormat;
}

/** One list an author can tabulate, with the fields of its rows — paths relative to one row. */
export interface DocumentCollectionPath {
  path: string;
  label: string;
  rowFields: DocumentFieldPath[];
}

export interface DocumentPaths {
  fields: DocumentFieldPath[];
  collections: DocumentCollectionPath[];
}

/** How deep single embedded objects are followed: `order.customer.address.city` is three below the port. */
const MAX_DEPTH = 3;
const MULTI_VALUED = new Set(['0..x', '0..n', '1..x', '1..n']);
const EMBEDDED = 'EMBEDDED_COMPONENTS';

/**
 * The paths a document's input ports offer, read from the entity definitions they name — what the Insert field
 * and table settings dialogs list, so an author picks `order.customerName` rather than typing it.
 *
 * - an `ENTITY_REF` port offers each attribute of its entity as a field, walks a single embedded object, and
 *   offers a multi-valued embedded attribute (`order.lineItems`) as a collection;
 * - an `ENTITY_COLLECTION` port is itself a collection;
 * - any other port is a field of its own.
 *
 * Foreign keys are offered as the stored id: paths do not follow relationships yet.
 */
@Injectable({ providedIn: 'root' })
export class DocumentPathCatalog {
  private readonly registry = inject(EntityDefinitionRegistry);

  async describe(ports: readonly DocumentInputPort[]): Promise<DocumentPaths> {
    const definitions = await this.registry.load();
    const paths: DocumentPaths = { fields: [], collections: [] };
    for (const port of ports) {
      if (port.type === PortType.ENTITY_REF || port.type === PortType.ENTITY_COLLECTION) {
        const definition = [...definitions.values()].find((candidate) => candidate.name === port.entityType);
        if (!definition) continue;
        if (port.type === PortType.ENTITY_COLLECTION) {
          paths.collections.push({ path: port.name, label: port.name, rowFields: rowFields(definition, definitions) });
        } else {
          walk(definition, port.name, port.name, 1, definitions, paths);
        }
      } else {
        paths.fields.push({ path: port.name, label: port.name, format: formatOfPortType(port.type) });
      }
    }
    return paths;
  }
}

function walk(definition: EntityDefinition, path: string, label: string, depth: number, definitions: ReadonlyMap<string, EntityDefinition>, paths: DocumentPaths): void {
  for (const attribute of sorted(definition)) {
    const attributePath = `${path}.${attribute.code}`;
    const attributeLabel = `${label} › ${attribute.name ?? attribute.code}`;
    if (attribute.formControlType === EMBEDDED && !definitions.has(attribute.linkedEntityType ?? '')) continue;
    const linked = attribute.formControlType === EMBEDDED ? definitions.get(attribute.linkedEntityType ?? '') : undefined;
    if (linked && isMultiValued(attribute)) {
      paths.collections.push({ path: attributePath, label: attributeLabel, rowFields: rowFields(linked, definitions) });
    } else if (linked) {
      if (depth < MAX_DEPTH) walk(linked, attributePath, attributeLabel, depth + 1, definitions, paths);
    } else if (!isMultiValued(attribute)) {
      paths.fields.push({ path: attributePath, label: attributeLabel, format: formatOf(attribute) });
    }
  }
}

/** The fields of one row, relative to it — the columns a table can show. */
function rowFields(definition: EntityDefinition, definitions: ReadonlyMap<string, EntityDefinition>): DocumentFieldPath[] {
  const collected: DocumentPaths = { fields: [], collections: [] };
  for (const attribute of sorted(definition)) {
    if (attribute.formControlType === EMBEDDED && !definitions.has(attribute.linkedEntityType ?? '')) continue;
    const linked = attribute.formControlType === EMBEDDED ? definitions.get(attribute.linkedEntityType ?? '') : undefined;
    const label = attribute.name ?? attribute.code;
    if (linked && !isMultiValued(attribute)) walk(linked, attribute.code, label, 2, definitions, collected);
    else if (!linked && !isMultiValued(attribute)) collected.fields.push({ path: attribute.code, label, format: formatOf(attribute) });
  }
  return collected.fields;
}

/** In display order; an attribute without one keeps its place after those that have it. */
function sorted(definition: EntityDefinition): EntityAttributeDefinition[] {
  return [...(definition.attributes ?? [])].sort((a, b) => (a.displayOrder ?? Number.MAX_SAFE_INTEGER) - (b.displayOrder ?? Number.MAX_SAFE_INTEGER));
}

function isMultiValued(attribute: EntityAttributeDefinition): boolean {
  return attribute.multiplicity !== undefined && MULTI_VALUED.has(attribute.multiplicity);
}

function formatOf(attribute: EntityAttributeDefinition): ValueFormat {
  switch (attribute.valueKind) {
    case 'NUMBER':
      return 'number';
    case 'DATE':
      return 'date';
    case 'DATE_TIME':
      return 'datetime';
    default:
      return 'text';
  }
}

function formatOfPortType(type: PortType): ValueFormat {
  if (type === PortType.NUMBER) return 'number';
  if (type === PortType.DATE) return 'date';
  return 'text';
}
