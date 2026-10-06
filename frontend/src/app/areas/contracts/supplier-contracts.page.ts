import { Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { ORDER_BADGE, OrderSummary, SupplierContractApi } from '../../core/api/contract.api';
import { formatDate, formatMoney } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

/** "Meus contratos": ordens de fornecimento do fornecedor. */
@Component({
  selector: 'app-supplier-contracts-page',
  imports: [RouterLink, MatIconModule, MatProgressSpinnerModule, AreaTabs],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Área do fornecedor</span>
        <h1>Meus contratos</h1>
        <p>
          As ordens de fornecimento ou de serviço que você recebeu: assine, acompanhe o recebimento,
          envie a nota fiscal e veja o pagamento.
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
          @for (o of items(); track o.id) {
            <li>
              <a class="row card-hover" [routerLink]="['/fornecedor/contratos', o.id]">
                <span class="icon-tile" aria-hidden="true"><mat-icon>receipt_long</mat-icon></span>
                <div class="main">
                  <strong>{{ o.tenderTitle }}</strong>
                  <span class="muted">{{ o.number }} · oportunidade {{ o.tenderNumber }}</span>
                </div>
                <div class="side">
                  <strong>{{ money(o.totalValue) }}</strong>
                  <span class="badge" [class]="badge[o.status]">{{ o.statusLabel }}</span>
                  @if (o.status === 'ISSUED' && o.signatureDueDate) {
                    <small class="muted">assine até {{ date(o.signatureDueDate) }}</small>
                  }
                </div>
              </a>
            </li>
          } @empty {
            <li class="empty">
              <mat-icon aria-hidden="true">inbox</mat-icon>
              <p>
                Você ainda não tem ordens de fornecimento. Elas aparecem aqui quando for
                selecionado.
              </p>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styleUrl: '../shared/list-page.scss',
})
export class SupplierContractsPage {
  private readonly api = inject(SupplierContractApi);

  protected readonly badge = ORDER_BADGE;
  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly items = signal<OrderSummary[] | null>(null);
  protected readonly error = signal('');

  constructor() {
    this.api.list().subscribe({
      next: (i) => this.items.set(i),
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }
}
