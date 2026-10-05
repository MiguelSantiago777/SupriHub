import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { AppRole } from '../models';
import { AuthService } from './auth.service';

/** Exige login; sem sessão, leva à tela de entrada e volta depois. */
export const authGuard: CanMatchFn = (_route, segments) => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  const returnUrl = '/' + segments.map((s) => s.path).join('/');
  return inject(Router).createUrlTree(['/entrar'], { queryParams: { voltar: returnUrl } });
};

/** Exige um dos perfis informados. */
export function roleGuard(...roles: AppRole[]): CanMatchFn {
  return (route, segments) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      const returnUrl = '/' + segments.map((s) => s.path).join('/');
      return router.createUrlTree(['/entrar'], { queryParams: { voltar: returnUrl } });
    }
    return auth.hasRole(...roles) ? true : router.createUrlTree(['/acesso-negado']);
  };
}

/**
 * Telas de login/cadastro: quem já está logado vai para a sua área.
 * Use em `canActivateChild` (nunca em `canMatch` de rota com path vazio: ela casa com
 * qualquer URL e o redirecionamento entraria em loop infinito).
 */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? inject(Router).createUrlTree([auth.homeRoute()]) : true;
};
