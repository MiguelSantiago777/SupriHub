import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  template: `
    <div class="container narrow">
      <div class="card">
        <h1>Página não encontrada</h1>
        <p>O endereço pode ter mudado ou estar incorreto.</p>
        <p><a routerLink="/">Voltar para o início</a></p>
      </div>
    </div>
  `,
})
export class NotFoundPage {}

@Component({
  selector: 'app-forbidden-page',
  imports: [RouterLink],
  template: `
    <div class="container narrow">
      <div class="card">
        <h1>Acesso negado</h1>
        <p>
          Seu perfil não tem acesso a esta área. Se precisar, fale com a equipe do Contrata + NATA.
        </p>
        <p><a routerLink="/">Voltar para o início</a></p>
      </div>
    </div>
  `,
})
export class ForbiddenPage {}
