import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AdminAuthService {
  private credentials: string | null = null;
  username = '';

  constructor(private readonly http: HttpClient) {}

  get authenticated(): boolean {
    return this.credentials !== null;
  }

  async login(username: string, password: string): Promise<void> {
    const encodedCredentials = btoa(Array.from(new TextEncoder().encode(`${username}:${password}`),
      (byte) => String.fromCharCode(byte)).join(''));
    this.credentials = encodedCredentials;
    try {
      const session = await firstValueFrom(this.http.get<{ username: string }>('/api/v1/admin/session'));
      this.username = session.username;
    } catch (error) {
      this.logout();
      throw error;
    }
  }

  logout(): void {
    this.credentials = null;
    this.username = '';
  }

  authorizationHeader(): string | null {
    return this.credentials === null ? null : `Basic ${this.credentials}`;
  }
}
