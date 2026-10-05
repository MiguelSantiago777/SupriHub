import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, of, shareReplay, tap, finalize, map, catchError } from 'rxjs';
import { AppRole, Me, MessageResponse, SessionResponse } from '../models';

/** Rota inicial de cada perfil (ordem = prioridade quando o usuário tem vários). */
export const ROLE_HOME: [AppRole, string][] = [
  ['ADMIN', '/admin'],
  ['COMMISSION', '/comissao'],
  ['BOARD', '/recursos'],
  ['INTERNAL_CONTROL', '/controle'],
  ['FINANCE', '/financeiro'],
  ['DEMAND_MANAGER', '/gestor'],
  ['SUPPLIER', '/fornecedor'],
];

/**
 * Sessão do usuário. O access token fica só em memória (signal); o refresh token é
 * um cookie HttpOnly que o JavaScript não lê. Ao abrir o app, tenta restaurar a sessão.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  private readonly me = signal<Me | null>(null);
  private refreshInFlight: Observable<string | null> | null = null;

  readonly user = this.me.asReadonly();
  readonly isAuthenticated = computed(() => this.me() !== null);
  readonly roles = computed(() => new Set(this.me()?.roles ?? []));

  accessToken(): string | null {
    return this.token();
  }

  hasRole(...roles: AppRole[]): boolean {
    const mine = this.roles();
    return roles.some((r) => mine.has(r));
  }

  homeRoute(): string {
    const mine = this.roles();
    return ROLE_HOME.find(([role]) => mine.has(role))?.[1] ?? '/';
  }

  /**
   * Chamado na inicialização: renova a sessão se o backend indicou que há uma
   * (cookie `suprihub_session`, sem segredo). Visitante anônimo não faz chamada.
   */
  restoreSession(): Promise<void> {
    if (!document.cookie.split('; ').some((c) => c.startsWith('suprihub_session=1'))) {
      return Promise.resolve();
    }
    return firstValueFrom(this.refresh().pipe(map(() => undefined)));
  }

  login(email: string, password: string): Observable<Me> {
    return this.http
      .post<SessionResponse>('/api/auth/login', { email, password }, { withCredentials: true })
      .pipe(
        tap((s) => this.setSession(s)),
        map((s) => s.user),
      );
  }

  register(fullName: string, email: string, password: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/register', { fullName, email, password });
  }

  confirmEmail(token: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/confirm-email', { token });
  }

  resendConfirmation(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/resend-confirmation', { email });
  }

  forgotPassword(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/forgot-password', { email });
  }

  resetPassword(token: string, newPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/reset-password', { token, newPassword });
  }

  /** Renova o access token (uma única chamada concorrente). Devolve null se a sessão expirou. */
  refresh(): Observable<string | null> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http
        .post<SessionResponse>('/api/auth/refresh', {}, { withCredentials: true })
        .pipe(
          tap((s) => this.setSession(s)),
          map((s) => s.accessToken),
          catchError(() => {
            this.clear();
            return of(null);
          }),
          finalize(() => (this.refreshInFlight = null)),
          shareReplay(1),
        );
    }
    return this.refreshInFlight;
  }

  logout(): void {
    this.http
      .post('/api/auth/logout', {}, { withCredentials: true })
      .pipe(catchError(() => of(null)))
      .subscribe(() => {
        this.clear();
        void this.router.navigateByUrl('/');
      });
  }

  /** Sessão perdida (ex.: refresh falhou durante o uso). */
  sessionExpired(): void {
    this.clear();
    void this.router.navigate(['/entrar'], { queryParams: { expirada: 1 } });
  }

  private setSession(s: SessionResponse): void {
    this.token.set(s.accessToken);
    this.me.set(s.user);
  }

  private clear(): void {
    this.token.set(null);
    this.me.set(null);
  }
}
