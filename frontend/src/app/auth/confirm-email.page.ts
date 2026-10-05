import { Component, inject, input, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { problemMessage } from '../core/http/problem';

@Component({
  selector: 'app-confirm-email-page',
  imports: [RouterLink, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="container narrow">
      <div class="card" aria-live="polite">
        <h1>Confirmação de e-mail</h1>
        @switch (state()) {
          @case ('loading') {
            <div role="status"><mat-spinner diameter="40" aria-label="Confirmando" /></div>
          }
          @case ('ok') {
            <p class="alert alert-success" role="status">{{ message() }}</p>
            <a mat-flat-button routerLink="/entrar">Entrar</a>
          }
          @default {
            <p class="alert alert-error" role="alert">{{ message() }}</p>
            <p><a routerLink="/reenviar-confirmacao">Pedir um novo link</a></p>
          }
        }
      </div>
    </div>
  `,
})
export class ConfirmEmailPage implements OnInit {
  private readonly auth = inject(AuthService);
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
    this.auth.confirmEmail(token).subscribe({
      next: (r) => {
        this.message.set(r.message);
        this.state.set('ok');
      },
      error: (e: unknown) => {
        this.message.set(problemMessage(e));
        this.state.set('error');
      },
    });
  }
}
