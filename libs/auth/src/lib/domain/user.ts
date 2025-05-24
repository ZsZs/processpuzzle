import { BaseEntity } from '@processpuzzle/base-entity';
import { v4 as uuidv4 } from 'uuid';

export class User implements BaseEntity {
  readonly id: string;
  private _email: string;
  private _firstName: string | undefined;
  private _lastName: string | undefined;
  private _photoUrl: string | undefined;

  constructor(email: string, id?: string, firstName?: string, lastName?: string, photoUrl?: string) {
    this._email = email;
    this.id = id ?? uuidv4();
    this.firstName = firstName ?? '';
    this.lastName = lastName ?? '';
    this.photoUrl = photoUrl ?? '';
  }

  // region properties
  public get email() {
    return this._email;
  }

  public set email(email: string) {
    this._email = email;
  }

  public get firstName(): string | undefined {
    return this._firstName;
  }

  public set firstName(firstName: string) {
    this._firstName = firstName;
  }

  public get lastName(): string | undefined {
    return this._lastName;
  }

  public set lastName(lastName: string) {
    this._lastName = lastName;
  }

  public get photoUrl(): string | undefined {
    return this._photoUrl;
  }

  public set photoUrl(url: string) {
    this._photoUrl = url;
  }

  // endregion
}
