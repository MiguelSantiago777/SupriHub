import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const isAuthEndpoint = (req: HttpRequest<unknown>) => req.url.startsWith('/api/auth/');

/**
 * Anexa o Bearer token às chamadas da API e, em caso de 401, tenta renovar a sessão
 * uma vez e repetir a requisição.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  if (!req.url.startsWith('/api/') || isAuthEndpoint(req)) {
    return next(req);
  }
  const withToken = (token: string | null) =>
    token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(withToken(auth.accessToken())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !auth.accessToken()) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        switchMap((token) => {
          if (!token) {
            auth.sessionExpired();
            return throwError(() => error);
          }
          return next(withToken(token));
        }),
      );
    }),
  );
};
