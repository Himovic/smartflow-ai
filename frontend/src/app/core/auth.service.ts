import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, tap } from 'rxjs';
import { Me } from './models';

interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

const ACCESS = 'sf.access';
const REFRESH = 'sf.refresh';
const TENANT = 'sf.tenant';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly me = signal<Me | null>(null);
  readonly tenantOverride = signal<string | null>(localStorage.getItem(TENANT));
  readonly isAuthenticated = computed(() => this.me() !== null);
  readonly modules = computed(() => new Set(Object.keys(this.me()?.permissions ?? {})));

  get accessToken(): string | null {
    return localStorage.getItem(ACCESS);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH);
  }

  login(email: string, password: string): Observable<TokenPair> {
    return this.http.post<TokenPair>('/api/v1/auth/login', { email, password }).pipe(tap((t) => this.store(t)));
  }

  refresh(): Observable<TokenPair> {
    return this.http.post<TokenPair>('/api/v1/auth/refresh', { refreshToken: this.refreshToken }).pipe(tap((t) => this.store(t)));
  }

  async loadMe(): Promise<Me | null> {
    if (!this.accessToken) return null;
    try {
      const me = await firstValueFrom(this.http.get<Me>('/api/v1/auth/me'));
      this.me.set(me);
      return me;
    } catch {
      return null;
    }
  }

  logout(redirect = true): void {
    const rt = this.refreshToken;
    if (rt) this.http.post('/api/v1/auth/logout', { refreshToken: rt }).subscribe({ error: () => undefined });
    localStorage.removeItem(ACCESS);
    localStorage.removeItem(REFRESH);
    localStorage.removeItem(TENANT);
    this.tenantOverride.set(null);
    this.me.set(null);
    if (redirect) this.router.navigateByUrl('/login');
  }

  /** Organization selector (SUPER_ADMIN only — enforced server side). */
  switchTenant(tenantId: string | null): void {
    if (tenantId && tenantId !== this.me()?.homeTenantId) localStorage.setItem(TENANT, tenantId);
    else localStorage.removeItem(TENANT);
    this.tenantOverride.set(localStorage.getItem(TENANT));
    location.assign('/dashboard');
  }

  can(module: string, action = 'read'): boolean {
    return this.me()?.permissions?.[module]?.includes(action) ?? false;
  }

  term(key: string, fallback: string): string {
    return this.me()?.tenant?.settings?.terminology?.[key] ?? fallback;
  }

  private store(t: TokenPair): void {
    localStorage.setItem(ACCESS, t.accessToken);
    localStorage.setItem(REFRESH, t.refreshToken);
  }
}
