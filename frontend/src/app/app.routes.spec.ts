import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { AppRole } from './core/models';

/** Navega com prazo: um loop de redirecionamento faria a promessa nunca resolver. */
async function navigate(url: string): Promise<string> {
  const router = TestBed.inject(Router);
  // Um loop nunca termina; 10 s cobrem com folga o carregamento sob demanda (lazy) das telas.
  const timeout = new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error(`Navegação para ${url} não terminou (loop?)`)), 10_000),
  );
  await Promise.race([router.navigateByUrl(url), timeout]);
  return router.url;
}

describe('Rotas da aplicação', { timeout: 15_000 }, () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  function login(roles: AppRole[]) {
    TestBed.inject(AuthService).login('a@b.com', 'Senha2026').subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/login')
      .flush({
        accessToken: 't',
        expiresAt: new Date().toISOString(),
        user: { id: 1, fullName: 'Admin', email: 'a@b.com', roles, emailVerified: true },
      });
  }

  it('usuário logado chega à sua área sem loop de redirecionamento', async () => {
    login(['ADMIN']);
    expect(await navigate('/admin')).toBe('/admin');
  });

  it('fornecedor logado chega à área do fornecedor', async () => {
    login(['SUPPLIER']);
    expect(await navigate('/fornecedor')).toBe('/fornecedor');
  });

  it('usuário logado que abre /entrar vai para a sua área', async () => {
    login(['COMMISSION']);
    expect(await navigate('/entrar')).toBe('/comissao/requerimentos');
  });

  it('visitante abre /entrar normalmente e é barrado nas áreas internas', async () => {
    expect(await navigate('/entrar')).toBe('/entrar');
    expect(await navigate('/admin')).toBe('/entrar?voltar=%2Fadmin');
  });
});
