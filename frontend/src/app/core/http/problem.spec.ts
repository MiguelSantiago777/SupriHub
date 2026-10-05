import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';
import { applyServerErrors, toProblem } from './problem';

describe('ProblemDetail (RFC 7807)', () => {
  it('traduz erro de rede em mensagem amigável', () => {
    const p = toProblem(new HttpErrorResponse({ status: 0 }));
    expect(p.code).toBe('NETWORK');
    expect(p.detail).toContain('Sem conexão');
  });

  it('usa o detail do backend', () => {
    const p = toProblem(
      new HttpErrorResponse({
        status: 422,
        error: { detail: 'Regra violada.', code: 'X', status: 422 },
      }),
    );
    expect(p.detail).toBe('Regra violada.');
    expect(p.code).toBe('X');
  });

  it('aplica erros de validação nos campos do formulário', () => {
    const form = new FormGroup({ email: new FormControl('') });
    const msg = applyServerErrors(
      form,
      new HttpErrorResponse({
        status: 400,
        error: {
          detail: 'Alguns campos precisam ser corrigidos.',
          errors: [{ field: 'email', message: 'E-mail inválido.' }],
        },
      }),
    );
    expect(msg).toBe('Alguns campos precisam ser corrigidos.');
    expect(form.controls.email.getError('server')).toBe('E-mail inválido.');
  });
});
