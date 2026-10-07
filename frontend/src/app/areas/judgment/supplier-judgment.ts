import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { JudgmentApi, MyJudgment } from '../../core/api/judgment.api';
import { formatDate, formatDateTime, formatMoney } from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';

/**
 * Situação do fornecedor depois do prazo: em julgamento, convocado a cobrir (Edital 8.2) ou
 * resultado homologado. Não mostra nada dos concorrentes além do valor vencedor.
 */
@Component({
  selector: 'app-supplier-judgment',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  template: `
    @if (mine(); as m) {
      @if (m.result; as r) {
        @if (r.winner) {
          <section class="status-card success" role="status">
            <mat-icon aria-hidden="true">emoji_events</mat-icon>
            <div>
              <h2>Sua proposta foi a selecionada!</h2>
              <p>
                Valor: <strong>{{ money(r.awardedValue) }}</strong
                >. O Instituto vai emitir a Ordem de Fornecimento. Fique atento ao e-mail e ao
                WhatsApp.
              </p>
              <a mat-stroked-button routerLink="/fornecedor/contratos">Ver em Meus contratos</a>
            </div>
          </section>
        } @else {
          <section class="status-card neutral" role="status">
            <mat-icon aria-hidden="true">fact_check</mat-icon>
            <div>
              <h2>Resultado</h2>
              <p>
                A proposta vencedora foi de <strong>{{ money(r.awardedValue) }}</strong> ({{
                  r.reason
                }}).
                @if (r.disqualified) {
                  A sua proposta foi desclassificada: {{ r.disqualifiedReason }}
                } @else if (r.myPosition) {
                  A sua ficou em <strong>{{ r.myPosition }}º lugar</strong>.
                }
              </p>
              <p class="muted">Obrigado por participar. Contamos com você nas próximas!</p>
              @if (r.canAppeal && !appealOpen()) {
                <p>
                  Discorda do resultado? Você pode recorrer até
                  <strong>{{ date(r.appealDeadline) }}</strong> (Anexo X).
                </p>
                <button mat-stroked-button type="button" (click)="appealOpen.set(true)">
                  <mat-icon aria-hidden="true">gavel</mat-icon> Recorrer do resultado
                </button>
              }
            </div>
          </section>
          @if (appealOpen() && r.canAppeal) {
            <section class="card appeal" aria-labelledby="appeal-title">
              <h2 id="appeal-title">Recurso contra o resultado</h2>
              <p class="muted">
                Explique os fatos e indique os itens do Edital ou da Política que fundamentam o
                pedido. A Comissão reconsidera em 3 dias úteis ou encaminha à Diretoria.
              </p>
              <mat-form-field class="full">
                <mat-label>Razões do recurso</mat-label>
                <textarea matInput rows="5" maxlength="5000" [(ngModel)]="reasons"></textarea>
              </mat-form-field>
              <mat-form-field class="full">
                <mat-label>Pedido</mat-label>
                <textarea matInput rows="2" maxlength="2000" [(ngModel)]="request"></textarea>
              </mat-form-field>
              <label class="file">
                <input
                  type="file"
                  accept=".pdf,.jpg,.jpeg,.png"
                  (change)="appealFile.set($any($event.target).files?.[0] ?? null)"
                />
                <span>{{ appealFile()?.name || 'Anexar documento (opcional)' }}</span>
              </label>
              @if (error()) {
                <p class="alert alert-error" role="alert">{{ error() }}</p>
              }
              <div class="row end">
                <button mat-button type="button" (click)="appealOpen.set(false)">Cancelar</button>
                <button
                  mat-flat-button
                  type="button"
                  [disabled]="busy() || !reasons.trim() || !request.trim()"
                  (click)="sendAppeal()"
                >
                  Enviar recurso
                </button>
              </div>
            </section>
          }
        }
        @if (m.appeal; as ap) {
          <section class="status-card info" role="status">
            <mat-icon aria-hidden="true">gavel</mat-icon>
            <div>
              <h2>Seu recurso: {{ ap.statusLabel }}</h2>
              <p>
                @switch (ap.status) {
                  @case ('FILED') {
                    Enviado em {{ dateTime(ap.filedAt) }}. A Comissão decide até
                    {{ date(ap.reconsiderationDue) }}.
                  }
                  @case ('FORWARDED') {
                    A Comissão manteve o resultado e encaminhou à Diretoria, que decide até
                    {{ date(ap.boardDue) }}. Motivação: {{ ap.commissionReason }}
                  }
                  @case ('GRANTED') {
                    Recurso provido: o julgamento foi reaberto e a Comissão vai julgar de novo.
                  }
                  @default {
                    Recurso não provido pela Diretoria: {{ ap.boardReason }}
                  }
                }
              </p>
            </div>
          </section>
        }
      } @else if (m.cover?.status === 'OFFERED' && m.cover?.canRespond) {
        @let c = m.cover!;
        <section class="status-card warning" role="status" aria-labelledby="cover-title">
          <mat-icon aria-hidden="true">price_change</mat-icon>
          <div class="grow">
            <h2 id="cover-title">Você pode ficar com esta oportunidade</h2>
            <p>
              A sua proposta ficou empatada com a de menor preço e você tem prioridade (item 8.2 do
              Edital). Para vencer, ofereça um valor
              <strong
                >{{ c.strictlyLower ? 'menor que' : 'igual ou menor que' }}
                {{ money(c.ceiling) }}</strong
              >
              até <strong>{{ date(c.dueDate) }}</strong
              >. Se não quiser, a vez passa à próxima colocada.
            </p>
            @if (error()) {
              <p class="alert alert-error" role="alert">{{ error() }}</p>
            }
            @if (!declining()) {
              <div class="row">
                <mat-form-field class="value">
                  <mat-label>Novo valor total (R$)</mat-label>
                  <input
                    matInput
                    type="number"
                    min="0.01"
                    step="0.01"
                    inputmode="decimal"
                    [(ngModel)]="value"
                  />
                  <mat-hint>Menor proposta: {{ money(c.ceiling) }}</mat-hint>
                </mat-form-field>
                <button
                  mat-flat-button
                  type="button"
                  [disabled]="busy() || !value || value <= 0"
                  (click)="cover()"
                >
                  Enviar novo valor
                </button>
                <button mat-stroked-button type="button" (click)="declining.set(true)">
                  Não quero cobrir
                </button>
              </div>
            } @else {
              <div class="row">
                <span>Confirma que não vai cobrir? A vez passa à próxima colocada.</span>
                <button mat-stroked-button type="button" (click)="declining.set(false)">
                  Voltar
                </button>
                <button
                  mat-flat-button
                  type="button"
                  class="danger"
                  [disabled]="busy()"
                  (click)="decline()"
                >
                  Confirmar
                </button>
              </div>
            }
          </div>
        </section>
      } @else {
        <section class="status-card neutral" role="status">
          <mat-icon aria-hidden="true">lock_clock</mat-icon>
          <div>
            <h2>Prazo de propostas encerrado</h2>
            <p>
              @if (m.cover?.status === 'COVERED') {
                Você ofereceu {{ money(m.cover!.offeredValue) }}. Aguarde a homologação do
                resultado.
              } @else if (m.cover) {
                {{ m.cover.statusLabel }}. A sua proposta segue no julgamento; o resultado chega por
                e-mail.
              } @else {
                A sua proposta está em julgamento. O resultado chega por e-mail.
              }
            </p>
          </div>
        </section>
      }
    }
  `,
  styleUrl: '../shared/status-card.scss',
  styles: `
    .grow {
      flex: 1 1 auto;
      min-width: 0;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 0.75rem;
      align-items: center;
      margin-top: 0.5rem;
    }
    .value {
      flex: 0 1 260px;
    }
    .danger {
      --mat-button-filled-container-color: var(--sh-error);
    }
    .appeal {
      margin-bottom: 1.5rem;
    }
    .appeal h2 {
      font-size: 1.2rem;
      margin: 0 0 0.5rem;
    }
    .full {
      width: 100%;
    }
    .end {
      justify-content: flex-end;
    }
    .file {
      display: inline-flex;
      position: relative;
      padding: 0.5rem 1rem;
      border: 1px dashed var(--sh-border-strong);
      border-radius: var(--sh-radius-sm);
      cursor: pointer;
      color: var(--sh-primary);
      font-weight: 700;
    }
    .file input {
      position: absolute;
      inset: 0;
      opacity: 0;
      cursor: pointer;
    }
    .file:focus-within {
      outline: 3px solid var(--sh-focus);
      outline-offset: 2px;
    }
  `,
})
export class SupplierJudgment {
  private readonly api = inject(JudgmentApi);
  private readonly snack = inject(MatSnackBar);

  readonly tenderId = input.required<number>();

  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;

  protected readonly mine = signal<MyJudgment | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly declining = signal(false);
  protected value: number | null = null;

  // Recurso contra o resultado
  protected readonly appealOpen = signal(false);
  protected readonly appealFile = signal<File | null>(null);
  protected reasons = '';
  protected request = 'Rever o julgamento.';

  constructor() {
    effect(() => {
      const id = this.tenderId();
      untracked(() => this.api.mine(id).subscribe({ next: (m) => this.mine.set(m) }));
    });
  }

  private run(call: Observable<MyJudgment>, ok: string): void {
    this.busy.set(true);
    this.error.set('');
    call.subscribe({
      next: (m) => {
        this.busy.set(false);
        this.mine.set(m);
        this.declining.set(false);
        this.snack.open(ok, 'OK', { duration: 6000 });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toProblem(e).detail ?? problemMessage(e));
      },
    });
  }

  protected cover(): void {
    if (this.value && this.value > 0) {
      this.run(
        this.api.cover(this.tenderId(), this.value),
        'Novo valor enviado. Aguarde a homologação.',
      );
    }
  }

  protected sendAppeal(): void {
    this.busy.set(true);
    this.error.set('');
    this.api
      .appeal(this.tenderId(), this.reasons.trim(), this.request.trim(), this.appealFile())
      .subscribe({
        next: (m) => {
          this.busy.set(false);
          this.mine.set(m);
          this.appealOpen.set(false);
          this.snack.open('Recurso enviado à Comissão.', 'OK', { duration: 6000 });
        },
        error: (e: unknown) => {
          this.busy.set(false);
          this.error.set(toProblem(e).detail ?? problemMessage(e));
        },
      });
  }

  protected decline(): void {
    this.run(
      this.api.declineCover(this.tenderId()),
      'Tudo certo. A vez passou à próxima colocada.',
    );
  }
}
