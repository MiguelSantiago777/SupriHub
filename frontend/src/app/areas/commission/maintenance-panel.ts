import { Component, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import {
  Application,
  CERTIFICATE_BADGE,
  CertificateStatus,
  ReviewApi,
} from '../../core/api/supplier.api';
import { formatDate, formatDateTime, todayIso } from '../../core/format';
import { problemMessage } from '../../core/http/problem';

/**
 * Manutenção do credenciado pela Comissão (Política art. 21, § 2º e 22; Manual item 3,
 * passo 8): conferir a validade das certidões, anexar a certidão obtida por consulta direta,
 * registrar a reconferência semestral e decidir a renovação.
 */
@Component({
  selector: 'app-maintenance-panel',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    @let a = app();
    @let m = a.maintenance!;
    <div class="card">
      <h2>Manutenção do cadastro</h2>
      @if (m.inactiveSince) {
        <p class="alert alert-error">
          <strong>Inativo temporariamente</strong> desde {{ date(m.inactiveSince) }}: certidão não
          regularizada no prazo. Volta a ficar ativo quando a certidão estiver em dia.
        </p>
      } @else if (m.irregularSince) {
        <p class="alert alert-warning">
          Certidão vencida desde {{ date(m.irregularSince) }}. Prazo do fornecedor:
          <strong>{{ date(m.regularizationDue) }}</strong
          >. Se possível, renove por consulta direta e anexe abaixo (Política, art. 21, § 2º).
        </p>
      }
      @if (m.deregistrationAlerted) {
        <p class="alert alert-error">
          Irregular há mais de 30 dias: cabe avaliar o descredenciamento, por decisão motivada
          (Política, art. 23).
        </p>
      }

      <h3>Certidões</h3>
      <ul class="certs">
        @for (c of m.certificates; track c.type) {
          <li>
            <div class="top">
              <span class="name">{{ c.label }}</span>
              <span class="badge" [class]="certBadge[c.state]">{{ c.stateLabel }}</span>
            </div>
            <small class="muted">
              @if (c.validUntil) {
                Válida até {{ date(c.validUntil) }}
              } @else {
                Sem validade informada
              }
              @if (c.verified) {
                · conferida
              }
            </small>
            @if (busy() === c.type) {
              <mat-spinner diameter="24" aria-label="Salvando" />
            } @else {
              <div class="cert-actions">
                <input
                  type="date"
                  [value]="dates()[c.type] ?? c.validUntil ?? ''"
                  (change)="setDate(c.type, $event)"
                  [attr.aria-label]="'Validade conferida: ' + c.label"
                />
                @if (c.documentId) {
                  <button mat-stroked-button type="button" (click)="confirmValidity(c)">
                    Conferida
                  </button>
                }
                <label class="attach" [attr.title]="'Anexar certidão obtida no site oficial'">
                  <input
                    type="file"
                    class="visually-hidden"
                    accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                    (change)="attach(c, $event)"
                  />
                  <mat-icon aria-hidden="true">attach_file</mat-icon> Anexar
                </label>
              </div>
            }
          </li>
        }
      </ul>

      <h3>Reconferência semestral</h3>
      <p class="small">
        @if (m.lastRecheckOn) {
          Última em {{ date(m.lastRecheckOn) }}.
        }
        Próxima até
        <strong [class.late]="m.recheckDue">{{ date(m.nextRecheckDue) }}</strong
        >.
      </p>
      <mat-form-field class="full">
        <mat-label>Observação (opcional)</mat-label>
        <textarea matInput [formControl]="recheckNote" rows="2" maxlength="3000"></textarea>
        <mat-hint>Ex.: conferidas no site da RFB, Caixa e TST; CEIS/CNEP sem registro.</mat-hint>
      </mat-form-field>
      <button
        mat-stroked-button
        type="button"
        class="full"
        [disabled]="busy() === 'recheck'"
        (click)="run('recheck')"
      >
        <mat-icon aria-hidden="true">fact_check</mat-icon> Registrar reconferência
      </button>

      @if (m.renewalRequestedAt) {
        <h3>Pedido de renovação</h3>
        <p class="small">
          Pedido em {{ dateTime(m.renewalRequestedAt) }}. Vale até
          <strong>{{ date(a.accreditationValidUntil) }}</strong
          >; renovado, ganha mais 24 meses a partir daí.
        </p>
        <mat-form-field class="full">
          <mat-label>Mensagem (obrigatória para devolver)</mat-label>
          <textarea matInput [formControl]="renewalNote" rows="3" maxlength="3000"></textarea>
        </mat-form-field>
        <div class="row-actions">
          <button
            mat-stroked-button
            type="button"
            [disabled]="busy() === 'return'"
            (click)="run('return')"
          >
            Devolver
          </button>
          <button
            mat-flat-button
            type="button"
            [disabled]="busy() === 'renew'"
            (click)="run('renew')"
          >
            <mat-icon aria-hidden="true">autorenew</mat-icon> Renovar
          </button>
        </div>
      }
      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      }
    </div>
  `,
  styles: `
    h2 {
      font-size: 1.2rem;
      margin: 0 0 0.75rem;
    }
    h3 {
      font-size: 1rem;
      margin: 1.25rem 0 0.5rem;
    }
    .alert {
      font-size: 0.92rem;
    }
    .small {
      font-size: 0.92rem;
      margin: 0 0 0.5rem;
    }
    .late {
      color: var(--sh-error);
    }
    .certs {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.5rem;
    }
    .certs li {
      display: grid;
      gap: 0.3rem;
      padding: 0.6rem 0.7rem;
      border: 1px solid var(--sh-border);
      border-radius: var(--sh-radius-sm);
    }
    .top {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 0.5rem;
    }
    .name {
      font-weight: 700;
      font-size: 0.92rem;
    }
    .cert-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
    }
    .cert-actions input[type='date'] {
      min-height: 36px;
      padding: 0 0.4rem;
      border: 1px solid var(--sh-border);
      border-radius: var(--sh-radius-sm);
      font: inherit;
      font-size: 0.9rem;
      background: var(--sh-surface);
      color: var(--sh-text);
    }
    .attach {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      min-height: 36px;
      padding: 0 0.75rem;
      border-radius: 999px;
      border: 1px solid var(--sh-primary);
      color: var(--sh-primary);
      font-weight: 700;
      font-size: 0.9rem;
      cursor: pointer;
    }
    .attach:focus-within {
      outline: 3px solid var(--sh-primary);
      outline-offset: 2px;
    }
    .attach mat-icon {
      width: 18px;
      height: 18px;
      font-size: 18px;
    }
    .full {
      width: 100%;
    }
    .row-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
    }
  `,
})
export class MaintenancePanel {
  private readonly api = inject(ReviewApi);
  private readonly snack = inject(MatSnackBar);

  readonly app = input.required<Application>();
  readonly changed = output<Application>();

  protected readonly certBadge = CERTIFICATE_BADGE;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;

  protected readonly busy = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly dates = signal<Record<string, string>>({});
  protected readonly recheckNote = new FormControl('', { nonNullable: true });
  protected readonly renewalNote = new FormControl('', { nonNullable: true });

  protected setDate(type: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.dates.update((d) => ({ ...d, [type]: value }));
  }

  private dateFor(c: CertificateStatus): string | null {
    const value = this.dates()[c.type] ?? c.validUntil;
    if (!value) {
      this.snack.open('Informe a validade da certidão.', 'OK', { duration: 5000 });
      return null;
    }
    return value;
  }

  /** Validade conferida no site oficial (corrige a data, se preciso). */
  protected confirmValidity(c: CertificateStatus): void {
    const validUntil = this.dateFor(c);
    if (validUntil && c.documentId) {
      this.call(
        c.type,
        this.api.setValidity(this.app().id, c.documentId, validUntil),
        'Validade conferida.',
      );
    }
  }

  /** Certidão obtida por consulta direta (Política art. 21, § 2º). */
  protected attach(c: CertificateStatus, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    const validUntil = this.dateFor(c);
    if (!validUntil) {
      return;
    }
    if (validUntil < todayIso()) {
      this.snack.open('A certidão anexada precisa estar válida.', 'OK', { duration: 5000 });
      return;
    }
    this.call(
      c.type,
      this.api.uploadCertificate(this.app().id, c.type, file, validUntil),
      'Certidão anexada e conferida.',
    );
  }

  protected run(action: 'recheck' | 'renew' | 'return'): void {
    const a = this.app();
    if (action === 'return' && !this.renewalNote.value.trim()) {
      this.error.set('Escreva o que o fornecedor precisa ajustar para devolver o pedido.');
      return;
    }
    const call =
      action === 'recheck'
        ? this.api.recheck(a.id, this.recheckNote.value.trim(), a.version)
        : action === 'renew'
          ? this.api.renew(a.id, this.renewalNote.value.trim(), a.version)
          : this.api.returnRenewal(a.id, this.renewalNote.value.trim(), a.version);
    this.call(
      action,
      call,
      action === 'recheck'
        ? 'Reconferência registrada.'
        : action === 'renew'
          ? 'Credenciamento renovado. O fornecedor foi avisado.'
          : 'Pedido devolvido ao fornecedor.',
    );
  }

  private call(key: string, request: Observable<Application>, done: string): void {
    this.busy.set(key);
    this.error.set('');
    request.subscribe({
      next: (app) => {
        this.busy.set(null);
        this.dates.set({});
        this.recheckNote.reset();
        this.renewalNote.reset();
        this.changed.emit(app);
        this.snack.open(done, 'OK', { duration: 4000 });
      },
      error: (e: unknown) => {
        this.busy.set(null);
        this.error.set(problemMessage(e));
      },
    });
  }
}
