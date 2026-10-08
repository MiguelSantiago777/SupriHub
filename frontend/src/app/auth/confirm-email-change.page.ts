import { Component, inject, input, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { AuthLayout } from './auth-layout';
import { AuthService } from '../core/auth/auth.service';
import { problemMessage } from '../core/http/problem';

/** Link enviado ao novo endereço quando a pessoa troca o e-mail em "Minha conta". */
@Component({
  selector: 'app-confirm-email-change-page',
  imports: [AuthLayout, RouterLink, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <app-auth-layout variant="default"
      ><div aria-live="polite">
        <h1>Novo e-mail</h1>
        @switch (state()) {
          @case ('loading') {
            <div role="status"><mat-spinner diameter="40" aria-label="Confirmando" /></div>
          }
          @case ('ok') {
            <p class="alert alert-success" role="status">{{ message() }}</p>
            @if (auth.isAuthenticated()) {
              <a mat-flat-button routerLink="/minha-conta">Voltar para Minha conta</a>
            } @else {
              <a mat-flat-button routerLink="/entrar">Entrar</a>
            }
          }
          @default {
            <p class="alert alert-error" role="alert">{{ message() }}</p>
            <p>Para tentar de novo, entre e peça a troca outra vez em "Minha conta".</p>
          }
        }
      </div>
    </app-auth-layout>
  `,
})
export class ConfirmEmailChangePage implements OnInit {
  protected readonly auth = inject(AuthService);
  readonly token = input<string>();
  protected readonly state = signal<'loading' | 'ok' | 'error'>('loading');
  protected readonly message = signal('');

  ngOnInit(): void {
    const token = this.token();
    if (!token) {
      this.state.set('error');
      this.message.set('Link inválido. Abra o link exatamente como chegou no seu e-mail.');
      return;
    }
    this.auth.confirmEmailChange(token).subscribe({
      next: (r) => {
        this.message.set(r.message);
        this.state.set('ok');
        this.auth.reloadUser();
      },
      error: (e: unknown) => {
        this.message.set(problemMessage(e));
        this.state.set('error');
      },
    });
  }
}
