import { BaseEntity } from '@processpuzzle/base-entity';
import { v4 as uuidv4 } from 'uuid';

export class User implements BaseEntity {
  readonly id: string;
  private _email: string | null | undefined;
  private _password: string | null | undefined;
  private _firstName: string | undefined;
  private _lastName: string | undefined;
  private _photoUrl: string | undefined;

  constructor(email?: string | null, id?: string, firstName?: string | null, lastName?: string | null, photoUrl?: string | null) {
    this._email = email;
    this.id = id ?? uuidv4();
    this.firstName = firstName ?? '';
    this.lastName = lastName ?? '';
    this.photoUrl = photoUrl ?? '';
  }

  // region properties
  public get email(): string | null | undefined {
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

  public get password(): string | null | undefined {
    return this._password;
  }

  public set password(password: string) {
    this._password = password;
  }

  public get photoUrl(): string | undefined {
    return this._photoUrl;
  }

  public set photoUrl(url: string) {
    this._photoUrl = url;
  }

  // endregion
}
