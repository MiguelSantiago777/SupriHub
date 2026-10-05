import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { AppRole, ROLE_LABELS } from '../core/models';

const DESCRIPTIONS: Partial<Record<AppRole, string[]>> = {
  COMMISSION: [
    'Fila de requerimentos com prazos (10 dias úteis) e diligências',
    'Análise de habilitação com checklist e consulta CEIS/CNEP/CEPIM',
    'Gestão do cadastro de credenciados e alertas de certidões',
    'Publicação e julgamento de editais',
  ],
  DEMAND_MANAGER: ['Criação do DFD-NATA (rascunho de edital)', 'Acompanhamento da execução'],
  FINANCE: ['Atesto de previsão orçamentária e saldo no DFD'],
  INTERNAL_CONTROL: [
    'Consulta (somente leitura) de todo o processo',
    'Registro de conflitos de interesse',
  ],
  BOARD: ['Aprovação das chamadas da Faixa 3', 'Decisão de recursos'],
};

/** Página inicial dos perfis internos (funcionalidades entram nas Fases 1 e 2). */
@Component({
  selector: 'app-area-home-page',
  imports: [RouterLink],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Área interna</span>
        <h1>{{ title() }}</h1>
        <p>Olá, {{ auth.user()?.fullName }}.</p>
      </div>
    </header>
    <div class="container">
      <section class="card">
        <h2>O que você fará aqui</h2>
        <ul>
          @for (item of items(); track item) {
            <li>{{ item }}</li>
          }
        </ul>
        <p class="muted">Estas funcionalidades estão em construção.</p>
        <p><a routerLink="/catalogo">Ver o catálogo de linhas</a></p>
      </section>
    </div>
  `,
})
export class AreaHomePage {
  protected readonly auth = inject(AuthService);
  /** Vem do `data` da rota. */
  readonly area = input.required<AppRole>();
  protected readonly title = computed(() => ROLE_LABELS[this.area()]);
  protected readonly items = computed(() => DESCRIPTIONS[this.area()] ?? []);
}
