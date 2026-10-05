import { Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'app-supplier-home-page',
  imports: [RouterLink, MatIconModule],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Área do fornecedor</span>
        <h1>Olá, {{ auth.user()?.fullName }}!</h1>
        <p>Esta é a sua área de fornecedor no Contrata + NATA.</p>
      </div>
    </header>
    <div class="container">
      <section class="card stack" aria-labelledby="proximo-passo">
        <h2 id="proximo-passo">Próximo passo: requerimento de credenciamento</h2>
        <p>
          Em breve você poderá preencher aqui o Requerimento de Credenciamento (Anexo II), enviar os
          documentos e acompanhar a análise da Comissão, com os prazos em dias úteis.
        </p>
        <div class="alert alert-info">
          <p>
            Enquanto isso, separe os documentos do seu tipo de negócio. Veja a lista no
            <a routerLink="/edital">Edital (item 4)</a> e as linhas no
            <a routerLink="/catalogo">Catálogo</a>.
          </p>
        </div>
      </section>
    </div>
  `,
})
export class SupplierHomePage {
  protected readonly auth = inject(AuthService);
}
