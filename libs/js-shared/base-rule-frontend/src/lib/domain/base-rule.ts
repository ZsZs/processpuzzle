import { BaseEntity } from '@processpuzzle/base-entity';
import { v4 as uuidv4 } from 'uuid';

export enum Severity {
  ERROR = 'ERROR',
  WARNING = 'WARNING',
  INFO = 'INFO',
}

export class BaseRule implements BaseEntity {
  readonly id: string;
  name: string;
  description: string | undefined;
  context: string;
  expression: string;
  severity: Severity;
  message: string | undefined;
  translocoId: string | undefined;
  extendsRuleId: string | undefined;
  override: boolean;
  enabled: boolean;
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  fields: string[] | undefined;

  constructor(
    id?: string,
    name?: string,
    description?: string,
    context?: string,
    expression?: string,
    severity?: Severity,
    message?: string,
    translocoId?: string,
    extendsRuleId?: string,
    override?: boolean,
    enabled?: boolean,
    version?: number,
    createdAt?: string,
    updatedAt?: string,
    fields?: string[],
  ) {
    this.id = id ? id : uuidv4();
    this.name = name != undefined ? name : 'BaseRule';
    this.description = description;
    this.context = context != undefined ? context : '';
    this.expression = expression != undefined ? expression : '';
    this.severity = severity != undefined ? severity : Severity.ERROR;
    this.message = message;
    this.translocoId = translocoId;
    this.extendsRuleId = extendsRuleId;
    this.override = override != undefined ? override : false;
    this.enabled = enabled != undefined ? enabled : true;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.fields = fields;
  }
}
