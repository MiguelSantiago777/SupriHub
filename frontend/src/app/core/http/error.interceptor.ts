import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { problemMessage } from './problem';

/**
 * Mostra aviso global para erros que o formulário não trata: sem conexão, excesso de
 * tentativas, acesso negado e falhas do servidor. Validação (400/422) fica com cada tela.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !req.url.startsWith('/api/auth/refresh')) {
        const global =
          error.status === 0 || error.status === 403 || error.status === 429 || error.status >= 500;
        if (global) {
          snackBar.open(problemMessage(error), 'Fechar', {
            duration: 8000,
            politeness: 'assertive',
          });
        }
      }
      return throwError(() => error);
    }),
  );
};
