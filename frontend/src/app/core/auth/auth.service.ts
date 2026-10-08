import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, of, shareReplay, tap, finalize, map, catchError } from 'rxjs';
import { AppRole, CodeRequiredResponse, Me, MessageResponse, SessionResponse } from '../models';

/** Rota inicial de cada perfil (ordem = prioridade quando o usuário tem vários). */
export const ROLE_HOME: [AppRole, string][] = [
  ['ADMIN', '/admin'],
  ['COMMISSION', '/comissao'],
  ['BOARD', '/diretoria'],
  ['INTERNAL_CONTROL', '/controle'],
  ['FINANCE', '/contratos'],
  ['DEMAND_MANAGER', '/contratos'],
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
  /** Avisa as outras abas quando a pessoa sai, para todas encerrarem a sessão juntas. */
  private readonly channel =
    typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('suprihub-auth');

  readonly user = this.me.asReadonly();
  readonly isAuthenticated = computed(() => this.me() !== null);
  readonly roles = computed(() => new Set(this.me()?.roles ?? []));

  constructor() {
    this.channel?.addEventListener('message', (e: MessageEvent) => {
      if (e.data === 'logout' && this.me()) {
        this.clear();
        void this.router.navigateByUrl('/');
      }
    });
  }

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

  /**
   * Primeira etapa: e-mail e senha. Devolve o usuário (sessão aberta) ou, com verificação em
   * duas etapas, o pedido de código enviado por e-mail.
   */
  login(email: string, password: string, captchaToken?: string): Observable<Me | CodeRequiredResponse> {
    return this.http
      .post<SessionResponse | CodeRequiredResponse>(
        '/api/auth/login',
        { email, password, captchaToken },
        { withCredentials: true },
      )
      .pipe(
        map((r) => {
          if ('codeRequired' in r) {
            return r;
          }
          this.setSession(r);
          return r.user;
        }),
      );
  }

  /** Segunda etapa: código de 6 dígitos recebido por e-mail. */
  verifyLoginCode(challenge: string, code: string): Observable<Me> {
    return this.http
      .post<SessionResponse>('/api/auth/login/code', { challenge, code }, { withCredentials: true })
      .pipe(
        tap((s) => this.setSession(s)),
        map((s) => s.user),
      );
  }

  resendLoginCode(challenge: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/login/resend-code', { challenge });
  }

  register(
    fullName: string,
    email: string,
    password: string,
    captchaToken?: string,
  ): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/register', {
      fullName,
      email,
      password,
      captchaToken,
    });
  }

  confirmEmail(token: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/confirm-email', { token });
  }

  resendConfirmation(email: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/resend-confirmation', { email });
  }

  forgotPassword(email: string, captchaToken?: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/forgot-password', { email, captchaToken });
  }

  checkResetLink(token: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/check-reset-link', { token });
  }

  // ---- Minha conta ----

  rename(fullName: string): Observable<Me> {
    return this.http.put<Me>('/api/me/name', { fullName }).pipe(tap((me) => this.me.set(me)));
  }

  /** Troca a senha; o backend encerra as outras sessões e devolve uma nova para esta aba. */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http
      .post<SessionResponse>(
        '/api/me/password',
        { currentPassword, newPassword },
        { withCredentials: true },
      )
      .pipe(
        tap((s) => this.setSession(s)),
        map(() => undefined),
      );
  }

  setTwoFactor(enabled: boolean, currentPassword: string): Observable<Me> {
    return this.http
      .put<Me>('/api/me/two-factor', { enabled, currentPassword })
      .pipe(tap((me) => this.me.set(me)));
  }

  requestEmailChange(newEmail: string, currentPassword: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/me/email', { newEmail, currentPassword });
  }

  confirmEmailChange(token: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/confirm-email-change', { token });
  }

  /** Recarrega os dados do usuário logado (ex.: depois de trocar o e-mail). */
  reloadUser(): void {
    if (this.me()) {
      this.http.get<Me>('/api/me').subscribe({ next: (me) => this.me.set(me), error: () => undefined });
    }
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
    this.endSession().subscribe(() => void this.router.navigateByUrl('/'));
  }

  /** Encerra a sessão no servidor, aqui e nas outras abas, sem sair da página atual. */
  endSession(): Observable<void> {
    return this.http.post('/api/auth/logout', {}, { withCredentials: true }).pipe(
      catchError(() => of(null)),
      tap(() => {
        this.clear();
        this.channel?.postMessage('logout');
      }),
      map(() => undefined),
    );
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
