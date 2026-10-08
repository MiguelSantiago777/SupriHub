import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SessionResponse } from '../models';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { roleGuard } from './guards';

/** Executa um CanMatchFn com os segmentos informados. */
const runGuard = (guard: ReturnType<typeof roleGuard>, path: string) =>
  TestBed.runInInjectionContext(() =>
    guard({ path }, path ? [{ path, parameters: {} } as never] : [], {} as never),
  );

const session = (
  token: string,
  roles: SessionResponse['user']['roles'] = ['SUPPLIER'],
): SessionResponse => ({
  accessToken: token,
  expiresAt: new Date(Date.now() + 900_000).toISOString(),
  user: {
    id: 1,
    fullName: 'Maria das Dores',
    email: 'maria@exemplo.com',
    roles,
    emailVerified: true,
    twoFactor: false,
    twoFactorMandatory: false,
  },
});

describe('Autenticação no frontend', () => {
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  function login(roles: SessionResponse['user']['roles'] = ['SUPPLIER'], token = 'tok-1') {
    auth.login('maria@exemplo.com', 'Lanche2026').subscribe();
    http.expectOne('/api/auth/login').flush(session(token, roles));
  }

  it('com verificação em duas etapas, só abre a sessão depois do código', () => {
    let first: unknown;
    auth.login('maria@exemplo.com', 'Lanche2026').subscribe((r) => (first = r));
    http
      .expectOne('/api/auth/login')
      .flush({ codeRequired: true, challenge: 'ch-1', sentTo: 'ma***@exemplo.com' });
    expect(first).toMatchObject({ codeRequired: true });
    expect(auth.isAuthenticated()).toBe(false);

    auth.verifyLoginCode('ch-1', '123456').subscribe();
    const req = http.expectOne('/api/auth/login/code');
    expect(req.request.body).toEqual({ challenge: 'ch-1', code: '123456' });
    req.flush(session('tok-2', ['COMMISSION']));
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.accessToken()).toBe('tok-2');
  });

  it('anexa o Bearer token às chamadas da API', () => {
    login();
    TestBed.inject(HttpClient).get('/api/me').subscribe();
    const req = http.expectOne('/api/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok-1');
    req.flush({});
  });

  it('não envia token para endpoints de autenticação', () => {
    login();
    auth.forgotPassword('maria@exemplo.com').subscribe();
    const req = http.expectOne('/api/auth/forgot-password');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({ message: 'ok' });
  });

  it('em 401, renova a sessão uma vez e repete a requisição', async () => {
    login();
    const result = firstValueFrom(TestBed.inject(HttpClient).get<{ ok: boolean }>('/api/me'));
    http.expectOne('/api/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/auth/refresh').flush(session('tok-2'));
    const retry = http.expectOne('/api/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer tok-2');
    retry.flush({ ok: true });
    await expect(result).resolves.toEqual({ ok: true });
  });

  it('leva cada perfil para a sua área', () => {
    login(['COMMISSION']);
    expect(auth.homeRoute()).toBe('/comissao');
    login(['SUPPLIER', 'ADMIN'], 'tok-3');
    expect(auth.homeRoute()).toBe('/admin');
  });

  it('roleGuard manda para o login quem não está autenticado, guardando o destino', () => {
    const result = runGuard(roleGuard('COMMISSION'), 'comissao') as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/entrar?voltar=%2Fcomissao');
  });

  it('roleGuard nega perfil sem permissão', () => {
    login(['SUPPLIER']);
    const result = runGuard(roleGuard('ADMIN'), 'admin') as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/acesso-negado');
    const allowed = runGuard(roleGuard('SUPPLIER'), 'fornecedor');
    expect(allowed).toBe(true);
  });
});
