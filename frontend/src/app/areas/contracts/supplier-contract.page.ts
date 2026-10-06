import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ORDER_BADGE, OrderDetail, SupplierContractApi } from '../../core/api/contract.api';
import { formatDate, formatMoney } from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';
import { OrderView } from './order-view';

function today(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Ordem do fornecedor: assinar, dar ciência do termo e enviar a nota fiscal. */
@Component({
  selector: 'app-supplier-contract-page',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    AreaTabs,
    OrderView,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <p class="back"><a routerLink="/fornecedor/contratos">← Meus contratos</a></p>
        @if (o(); as d) {
          <div class="head">
            <div>
              <span class="eyebrow">{{ d.number }} · oportunidade {{ d.tender.number }}</span>
              <h1>{{ d.tender.title }}</h1>
              <p>
                <strong>{{ money(d.totalValue) }}</strong>
              </p>
            </div>
            <span class="badge big" [class]="badge[d.status]">{{ d.statusLabel }}</span>
          </div>
        } @else {
          <h1>Ordem de fornecimento</h1>
        }
      </div>
    </header>
    <app-area-tabs area="supplier" />

    <div class="container page">
      @if (loadError()) {
        <p class="alert alert-error" role="alert">{{ loadError() }}</p>
      } @else if (!o()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        @let d = o()!;
        @if (error()) {
          <div class="alert alert-error" role="alert">
            <p>
              <strong>{{ error() }}</strong>
            </p>
            @for (i of issues(); track i.field) {
              <p>{{ i.message }}</p>
            }
          </div>
        }

        @if (d.canSign) {
          <section class="status-card warning" aria-labelledby="s-sign">
            <mat-icon aria-hidden="true">draw</mat-icon>
            <div class="grow">
              <h2 id="s-sign">Assine a ordem até {{ date(d.signatureDueDate) }}</h2>
              <p>
                Leia a ordem (botão "Ordem em PDF") e as cláusulas. Sem assinatura no prazo, o
                Instituto pode convocar o próximo (Edital 9.1).
              </p>
              <mat-checkbox [(ngModel)]="agree"
                >Li e aceito as cláusulas desta ordem, o Edital e a Política de
                Credenciamento.</mat-checkbox
              >
              <div class="btns">
                <button mat-stroked-button type="button" (click)="pdf()">
                  <mat-icon aria-hidden="true">picture_as_pdf</mat-icon> Ordem em PDF
                </button>
                <button
                  mat-flat-button
                  type="button"
                  [disabled]="busy() || !agree"
                  (click)="sign()"
                >
                  Assinar eletronicamente
                </button>
              </div>
            </div>
          </section>
        } @else if (d.status === 'SIGNED') {
          <section class="status-card info" role="status">
            <mat-icon aria-hidden="true">local_shipping</mat-icon>
            <div>
              <h2>Ordem assinada — hora de executar</h2>
              <p>
                Execute conforme a ordem ({{ d.schedule || 'data combinada'
                }}{{ d.location ? ', ' + d.location : '' }}). Depois, o gestor registra o
                recebimento e a sua avaliação.
              </p>
            </div>
          </section>
        } @else if (d.status === 'RECEIVED_WITH_RESERVATIONS') {
          <section class="status-card warning" role="status">
            <mat-icon aria-hidden="true">build</mat-icon>
            <div>
              <h2>Recebido com ressalvas</h2>
              <p>
                Veja abaixo as correções pedidas e o prazo. Depois o gestor registra o recebimento
                final.
              </p>
            </div>
          </section>
        } @else if (d.status === 'REJECTED') {
          <section class="status-card neutral" role="status">
            <mat-icon aria-hidden="true">block</mat-icon>
            <div>
              <h2>Objeto recusado</h2>
              <p>Veja o motivo no termo abaixo. Em caso de dúvida, fale com o Instituto.</p>
            </div>
          </section>
        } @else if (d.status === 'INVOICED') {
          <section class="status-card info" role="status">
            <mat-icon aria-hidden="true">hourglass_top</mat-icon>
            <div>
              <h2>Nota fiscal recebida</h2>
              <p>
                O pagamento sai por Pix ou transferência para a sua conta até
                {{ date(d.paymentDueDate) }}.
              </p>
            </div>
          </section>
        } @else if (d.status === 'PAID') {
          <section class="status-card success" role="status">
            <mat-icon aria-hidden="true">verified</mat-icon>
            <div>
              <h2>Pago</h2>
              <p>
                {{ money(d.paidValue) }} em {{ date(d.paidOn) }} ({{ d.paymentMethodLabel }}). O
                comprovante está nos arquivos abaixo. Obrigado!
              </p>
            </div>
          </section>
        }

        @if (lastReceiptPending()) {
          <section class="card ack">
            <p>
              O Instituto registrou o <strong>Termo de Recebimento e Avaliação</strong>. Confira as
              notas abaixo e dê ciência.
            </p>
            <button mat-stroked-button type="button" [disabled]="busy()" (click)="ack()">
              Dar ciência do termo
            </button>
          </section>
        }

        @if (d.status === 'RECEIVED') {
          <section class="card" aria-labelledby="s-nf">
            <h2 id="s-nf">Envie a nota fiscal ou o RPA</h2>
            <p class="muted">
              Com a data, o valor, a descrição, o CNPJ do Instituto e o seu CNPJ/CPF. O pagamento
              sai em até {{ d.paymentDays }} dias após o recebimento.
            </p>
            <div class="three">
              <mat-form-field>
                <mat-label>Número da nota / RPA</mat-label>
                <input matInput [(ngModel)]="number" maxlength="60" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Data de emissão</mat-label>
                <input matInput type="date" [(ngModel)]="date_" [max]="todayIso" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Valor (R$)</mat-label>
                <input matInput type="number" step="0.01" [(ngModel)]="value" />
              </mat-form-field>
            </div>
            <div class="btns">
              <label class="file-btn">
                <input type="file" accept=".pdf,.jpg,.jpeg,.png" (change)="pick($event)" />
                <span>{{ file()?.name || 'Escolher arquivo (PDF ou foto)' }}</span>
              </label>
              <button
                mat-flat-button
                type="button"
                [disabled]="busy() || !file()"
                (click)="invoice()"
              >
                Enviar nota fiscal
              </button>
            </div>
          </section>
        }

        @if (!d.canSign) {
          <div class="btns top">
            <button mat-stroked-button type="button" (click)="pdf()">
              <mat-icon aria-hidden="true">picture_as_pdf</mat-icon> Ordem em PDF
            </button>
          </div>
        }
        <app-order-view [order]="d" (openFile)="openFile($event)" />
      }
    </div>
  `,
  styleUrls: ['../shared/status-card.scss'],
  styles: `
    .back {
      margin: 0 0 0.75rem !important;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: flex-end;
      gap: 1rem;
    }
    .badge.big {
      font-size: 0.95rem;
      padding: 0.3rem 0.9rem;
    }
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
    }
    .loading {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 2rem 0;
    }
    .grow {
      flex: 1 1 auto;
      min-width: 0;
    }
    .btns {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: center;
      margin-top: 0.75rem;
    }
    .btns.top {
      margin: 0 0 1rem;
    }
    .card {
      margin-bottom: 1rem;
      h2 {
        font-size: 1.2rem;
        margin: 0 0 0.5rem;
      }
    }
    .ack {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: center;
      justify-content: space-between;
      p {
        margin: 0;
      }
    }
    .three {
      display: grid;
      gap: 0 0.75rem;
      @media (min-width: 760px) {
        grid-template-columns: 1fr 1fr 1fr;
      }
    }
    .file-btn {
      position: relative;
      display: inline-flex;
      align-items: center;
      min-height: 48px;
      padding: 0 1rem;
      border: 1px solid var(--sh-border-strong);
      border-radius: 999px;
      font-weight: 700;
      cursor: pointer;
      color: var(--sh-primary);
      input {
        position: absolute;
        inset: 0;
        opacity: 0;
        cursor: pointer;
      }
      &:focus-within {
        outline: 3px solid var(--sh-focus);
        outline-offset: 2px;
      }
    }
  `,
})
export class SupplierContractPage {
  private readonly api = inject(SupplierContractApi);
  private readonly snack = inject(MatSnackBar);

  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  protected readonly badge = ORDER_BADGE;
  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly todayIso = today();

  protected readonly o = signal<OrderDetail | null>(null);
  protected readonly loadError = signal('');
  protected readonly error = signal('');
  protected readonly issues = signal<{ field: string; message: string }[]>([]);
  protected readonly busy = signal(false);
  protected readonly file = signal<File | null>(null);
  protected agree = false;
  protected number = '';
  protected date_ = today();
  protected value: number | null = null;

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() =>
        this.api.get(id).subscribe({
          next: (o) => this.set(o),
          error: (e: unknown) => this.loadError.set(problemMessage(e)),
        }),
      );
    });
  }

  protected lastReceiptPending(): boolean {
    const r = this.o()?.receipts ?? [];
    return r.length > 0 && !r[r.length - 1].supplierAckAt;
  }

  private set(o: OrderDetail): void {
    this.o.set(o);
    this.value = o.totalValue;
  }

  private run(call: Observable<OrderDetail>, ok: string): void {
    this.busy.set(true);
    this.error.set('');
    this.issues.set([]);
    call.subscribe({
      next: (o) => {
        this.busy.set(false);
        this.set(o);
        this.snack.open(ok, 'OK', { duration: 6000 });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const p = toProblem(e);
        this.error.set(p.detail ?? problemMessage(e));
        this.issues.set(p.errors ?? []);
      },
    });
  }

  protected sign(): void {
    const o = this.o();
    if (o && this.agree) {
      this.run(this.api.sign(o.id), 'Ordem assinada. Bom trabalho!');
    }
  }

  protected ack(): void {
    const o = this.o();
    if (o) {
      this.run(this.api.acknowledge(o.id), 'Ciência registrada.');
    }
  }

  protected pick(event: Event): void {
    const el = event.target as HTMLInputElement;
    this.file.set(el.files?.[0] ?? null);
  }

  protected invoice(): void {
    const o = this.o();
    if (o) {
      this.run(
        this.api.invoice(
          o.id,
          { number: this.number, date: this.date_, value: this.value },
          this.file(),
        ),
        'Nota fiscal enviada. Agora é com o Financeiro.',
      );
    }
  }

  protected openFile(fileId: number): void {
    const o = this.o();
    if (o) {
      this.api.openFile(o.id, fileId);
    }
  }

  protected pdf(): void {
    const o = this.o();
    if (o) {
      this.api.openPdf(o.id);
    }
  }
}
