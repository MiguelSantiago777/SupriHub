import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';
import { ProblemDetail } from '../models';

const GENERIC = 'Não foi possível concluir a ação. Tente novamente em instantes.';

/** Extrai o ProblemDetail (RFC 7807) de um erro HTTP. */
export function toProblem(error: unknown): ProblemDetail {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return {
        status: 0,
        code: 'NETWORK',
        detail: 'Sem conexão com o servidor. Verifique sua internet.',
      };
    }
    const body = error.error as ProblemDetail | null;
    if (body && typeof body === 'object' && (body.detail || body.code)) {
      return { ...body, status: body.status ?? error.status };
    }
    return { status: error.status, detail: GENERIC };
  }
  return { detail: GENERIC };
}

export function problemMessage(error: unknown): string {
  return toProblem(error).detail ?? GENERIC;
}

/**
 * Aplica os erros de validação do backend aos campos do formulário (quando os nomes
 * coincidem). Devolve a mensagem geral para exibir no topo.
 */
export function applyServerErrors(form: FormGroup, error: unknown): string {
  const problem = toProblem(error);
  for (const e of problem.errors ?? []) {
    const control: AbstractControl | null = form.get(e.field);
    if (control) {
      control.setErrors({ server: e.message });
      control.markAsTouched();
    }
  }
  return problem.detail ?? GENERIC;
}
