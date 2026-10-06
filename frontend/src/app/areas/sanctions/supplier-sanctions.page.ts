import { Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { SANCTION_BADGE, SanctionSummary, SupplierSanctionApi } from '../../core/api/control.api';
import { formatDate } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

/** Processos sancionadores do próprio fornecedor (chegam também por e-mail). */
@Component({
  selector: 'app-supplier-sanctions-page',
  imports: [RouterLink, MatIconModule, MatProgressSpinnerModule, AreaTabs],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Área do fornecedor</span>
        <h1>Processos</h1>
        <p>
          Notificações do Instituto sobre a execução de contratos. Você sempre pode se defender.
        </p>
      </div>
    </header>
    <app-area-tabs area="supplier" />

    <div class="container page">
      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      } @else if (!items()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        <ul class="list">
          @for (p of items(); track p.id) {
            <li>
              <a class="row card-hover" [routerLink]="['/fornecedor/sancoes', p.id]">
                <span class="icon-tile" aria-hidden="true"><mat-icon>gavel</mat-icon></span>
                <div class="main">
                  <strong>{{ p.infractionLabel }}</strong>
                  <span class="muted">{{ p.number }}</span>
                </div>
                <div class="side">
                  <span class="badge" [class]="badge[p.status]">{{ p.statusLabel }}</span>
                  @if (p.nextDue) {
                    <small class="muted">prazo {{ date(p.nextDue) }}</small>
                  }
                </div>
              </a>
            </li>
          } @empty {
            <li class="empty">
              <mat-icon aria-hidden="true">verified</mat-icon>
              <p>Nenhum processo. Tudo certo!</p>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styleUrl: '../shared/list-page.scss',
})
export class SupplierSanctionsPage {
  private readonly api = inject(SupplierSanctionApi);

  protected readonly badge = SANCTION_BADGE;
  protected readonly date = formatDate;
  protected readonly items = signal<SanctionSummary[] | null>(null);
  protected readonly error = signal('');

  constructor() {
    this.api.list().subscribe({
      next: (i) => this.items.set(i),
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }
}
