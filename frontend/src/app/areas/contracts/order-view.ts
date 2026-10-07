import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { OrderDetail, Receipt, SCORE_CRITERIA } from '../../core/api/contract.api';
import {
  formatDate,
  formatDateTime,
  formatMoney,
  formatQuantity,
  formatSize,
} from '../../core/format';

/** Dados da ordem, itens, termos de recebimento, nota, pagamento e arquivos (só leitura). */
@Component({
  selector: 'app-order-view',
  imports: [MatButtonModule, MatIconModule],
  template: `
    @let o = order();
    <section class="card" aria-labelledby="ov-obj">
      <h2 id="ov-obj"><mat-icon aria-hidden="true">description</mat-icon> {{ o.kindLabel }}</h2>
      <p class="pre">{{ o.objectDescription }}</p>
      <div class="table-wrap">
        <table class="data mini">
          <thead>
            <tr>
              <th scope="col">Item</th>
              <th scope="col">Unid.</th>
              <th scope="col" class="num">Qtd.</th>
              <th scope="col" class="num">Preço unit.</th>
            </tr>
          </thead>
          <tbody>
            @for (i of o.items; track i.position) {
              <tr>
                <td>{{ i.position }}. {{ i.description }}</td>
                <td>{{ i.unit }}</td>
                <td class="num">{{ qty(i.quantity) }}</td>
                <td class="num">
                  {{ i.unitPrice === null ? 'na proposta' : money(i.unitPrice) }}
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <dl class="grid">
        <div>
          <dt>Valor total</dt>
          <dd>
            <strong>{{ money(o.totalValue) }}</strong>
          </dd>
        </div>
        <div>
          <dt>Pagamento</dt>
          <dd>até {{ o.paymentDays }} dias após o recebimento definitivo</dd>
        </div>
        @if (o.upfrontValue) {
          <div>
            <dt>Sinal</dt>
            <dd>{{ money(o.upfrontValue) }}</dd>
          </div>
        }
        @if (o.penaltyPct) {
          <div>
            <dt>Multa por inexecução</dt>
            <dd>{{ o.penaltyPct }}%</dd>
          </div>
        }
        @if (o.schedule) {
          <div>
            <dt>Quando</dt>
            <dd>{{ o.schedule }}</dd>
          </div>
        }
        @if (o.location) {
          <div>
            <dt>Onde</dt>
            <dd>{{ o.location }}</dd>
          </div>
        }
        @if (o.project) {
          <div>
            <dt>Projeto / rubrica</dt>
            <dd>{{ o.project }}</dd>
          </div>
        }
        @if (o.issuedAt) {
          <div>
            <dt>Emitida em</dt>
            <dd>{{ dateTime(o.issuedAt) }}</dd>
          </div>
        }
        @if (o.signedAt) {
          <div>
            <dt>{{ o.signedVia === 'ACCEPTANCE' ? 'Aceite eletrônico' : 'Assinada em' }}</dt>
            <dd>{{ dateTime(o.signedAt) }}</dd>
          </div>
        }
      </dl>
      @if (o.notes) {
        <p class="muted small">{{ o.notes }}</p>
      }
    </section>

    @if (o.receipts.length) {
      <section class="card" aria-labelledby="ov-rec">
        <h2 id="ov-rec">
          <mat-icon aria-hidden="true">fact_check</mat-icon> Recebimento e avaliação
        </h2>
        @for (r of o.receipts; track r.id) {
          <article class="receipt">
            <div class="r-head">
              <strong>{{ r.kindLabel }}</strong>
              <span class="badge" [class]="avgBadge(r)">média {{ r.average }}</span>
            </div>
            <p class="muted small">
              Execução em {{ date(r.executionDate) }} · termo de {{ dateTime(r.receivedAt) }}
              @if (r.supplierAckAt) {
                · ciência do fornecedor em {{ dateTime(r.supplierAckAt) }}
              } @else {
                · aguardando ciência do fornecedor
              }
            </p>
            @if (r.reservations) {
              <p>
                <strong>Ressalvas:</strong> {{ r.reservations }} (correção em
                {{ r.correctionDays }} dias)
              </p>
            }
            @if (r.rejectReason) {
              <p><strong>Motivo da recusa:</strong> {{ r.rejectReason }}</p>
            }
            <table class="data mini">
              <tbody>
                @for (c of criteria; track c.key) {
                  <tr>
                    <td>{{ c.label }}</td>
                    <td class="num">{{ score(r, c.key) }}</td>
                  </tr>
                }
              </tbody>
            </table>
            @if (r.notes) {
              <p class="small">{{ r.notes }}</p>
            }
            @if (r.evidenceKinds) {
              <p class="muted small">Evidências: {{ r.evidenceKinds }}</p>
            }
          </article>
        }
      </section>
    }

    @if (o.invoiceNumber || o.paidOn || o.upfrontPaidOn) {
      <section class="card" aria-labelledby="ov-pay">
        <h2 id="ov-pay">
          <mat-icon aria-hidden="true">payments</mat-icon> Nota fiscal e pagamento
        </h2>
        <dl class="grid">
          @if (o.invoiceNumber) {
            <div>
              <dt>Nota fiscal / RPA</dt>
              <dd>
                {{ o.invoiceNumber }} de {{ date(o.invoiceDate) }} — {{ money(o.invoiceValue) }}
              </dd>
            </div>
          }
          @if (o.upfrontPaidOn) {
            <div>
              <dt>Sinal (Edital 10.4)</dt>
              <dd>
                {{ date(o.upfrontPaidOn) }} — {{ money(o.upfrontPaidValue) }} ({{
                  o.upfrontPaymentMethodLabel
                }})
              </dd>
            </div>
            <div>
              <dt>Saldo</dt>
              <dd>{{ money(o.remainingValue) }}</dd>
            </div>
          }
          @if (o.paymentDueDate) {
            <div>
              <dt>Pagar até</dt>
              <dd>{{ date(o.paymentDueDate) }}</dd>
            </div>
          }
          @if (o.paidOn) {
            <div>
              <dt>Pago em</dt>
              <dd>{{ date(o.paidOn) }} — {{ money(o.paidValue) }} ({{ o.paymentMethodLabel }})</dd>
            </div>
          }
        </dl>
      </section>
    }

    @if (o.files.length) {
      <section class="card" aria-labelledby="ov-files">
        <h2 id="ov-files"><mat-icon aria-hidden="true">folder</mat-icon> Arquivos</h2>
        <ul class="files">
          @for (f of o.files; track f.id) {
            <li>
              <button mat-button type="button" (click)="openFile.emit(f.id)">
                <mat-icon aria-hidden="true">attach_file</mat-icon> {{ f.name }}
              </button>
              <span class="muted small"
                >{{ f.kindLabel }} · {{ size(f.size) }} · {{ dateTime(f.uploadedAt) }}</span
              >
            </li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 1rem;
      min-width: 0;
    }
    .card h2 {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 1.2rem;
      margin: 0 0 0.85rem;
      mat-icon {
        color: var(--sh-primary);
      }
    }
    .pre {
      white-space: pre-line;
    }
    .small {
      font-size: 0.9rem;
    }
    .num {
      text-align: right;
      white-space: nowrap;
    }
    .mini {
      font-size: 0.92rem;
      margin: 0.5rem 0;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 0.6rem 1rem;
      margin: 0.75rem 0 0;
    }
    dt {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--sh-text-muted);
    }
    dd {
      margin: 0.1rem 0 0;
    }
    .receipt + .receipt {
      margin-top: 1rem;
      padding-top: 1rem;
      border-top: 1px solid var(--sh-border);
    }
    .r-head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .files {
      list-style: none;
      padding: 0;
      margin: 0;
      li {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.25rem 0.5rem;
      }
    }
  `,
})
export class OrderView {
  readonly order = input.required<OrderDetail>();
  readonly openFile = output<number>();

  protected readonly criteria = SCORE_CRITERIA;
  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly qty = formatQuantity;
  protected readonly size = formatSize;

  protected score(r: Receipt, key: string): number {
    return (r as unknown as Record<string, number>)[key];
  }

  protected avgBadge(r: Receipt): string {
    return r.average >= 4 ? 'badge-success' : r.average >= 3 ? 'badge-info' : 'badge-error';
  }
}
