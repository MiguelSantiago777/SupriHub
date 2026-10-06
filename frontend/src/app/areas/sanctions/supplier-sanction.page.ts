import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { SANCTION_BADGE, SanctionDetail, SupplierSanctionApi } from '../../core/api/control.api';
import { formatDate } from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';
import { SanctionView } from './sanction-view';

/** O fornecedor no processo sancionador: defesa e recurso (contraditório e ampla defesa). */
@Component({
  selector: 'app-supplier-sanction-page',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    AreaTabs,
    SanctionView,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <p class="back"><a routerLink="/fornecedor/sancoes">← Processos</a></p>
        @if (p(); as d) {
          <div class="head">
            <div>
              <span class="eyebrow">Processo {{ d.number }}</span>
              <h1>{{ d.infractionLabel }}</h1>
            </div>
            <span class="badge big" [class]="badge[d.status]">{{ d.statusLabel }}</span>
          </div>
        } @else {
          <h1>Processo</h1>
        }
      </div>
    </header>
    <app-area-tabs area="supplier" />

    <div class="container page">
      @if (loadError()) {
        <p class="alert alert-error" role="alert">{{ loadError() }}</p>
      } @else if (!p()) {
        <div class="loading" role="status"><mat-spinner diameter="36" /> Carregando…</div>
      } @else {
        @let d = p()!;
        @if (error()) {
          <p class="alert alert-error" role="alert">{{ error() }}</p>
        }
        @if (d.canDefend || d.canAppeal) {
          <section class="card form">
            <h2>{{ d.canDefend ? 'Apresente a sua defesa' : 'Recorra à Diretoria' }}</h2>
            <p class="muted">
              @if (d.canDefend) {
                Explique o que aconteceu e junte o que comprova (fotos, documentos). Prazo:
                <strong>{{ date(d.defenseDue) }}</strong
                >.
              } @else {
                Você pode pedir à Diretoria que reveja a decisão. Prazo:
                <strong>{{ date(d.appealDue) }}</strong
                >.
              }
            </p>
            <mat-form-field class="full">
              <mat-label>{{ d.canDefend ? 'Sua defesa' : 'Razões do recurso' }}</mat-label>
              <textarea matInput rows="6" [(ngModel)]="text" maxlength="5000"></textarea>
            </mat-form-field>
            <div class="btns">
              <label class="file-btn">
                <input type="file" accept=".pdf,.jpg,.jpeg,.png,.docx" (change)="pick($event)" />
                <span>{{ file()?.name || 'Anexar arquivo (opcional)' }}</span>
              </label>
              <button
                mat-flat-button
                type="button"
                [disabled]="busy() || !text.trim()"
                (click)="send()"
              >
                {{ d.canDefend ? 'Enviar defesa' : 'Enviar recurso' }}
              </button>
            </div>
          </section>
        }
        <section class="card">
          <app-sanction-view [process]="d" (openFile)="openFile($event)" />
        </section>
      }
    </div>
  `,
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
      display: grid;
      gap: 1rem;
    }
    .loading {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 2rem 0;
    }
    h2 {
      font-size: 1.2rem;
      margin: 0 0 0.5rem;
    }
    .full {
      width: 100%;
    }
    .btns {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: center;
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
export class SupplierSanctionPage {
  private readonly api = inject(SupplierSanctionApi);
  private readonly snack = inject(MatSnackBar);

  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  protected readonly badge = SANCTION_BADGE;
  protected readonly date = formatDate;
  protected readonly p = signal<SanctionDetail | null>(null);
  protected readonly loadError = signal('');
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly file = signal<File | null>(null);
  protected text = '';

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() =>
        this.api.get(id).subscribe({
          next: (p) => this.p.set(p),
          error: (e: unknown) => this.loadError.set(problemMessage(e)),
        }),
      );
    });
  }

  protected pick(event: Event): void {
    const el = event.target as HTMLInputElement;
    this.file.set(el.files?.[0] ?? null);
  }

  protected send(): void {
    const d = this.p();
    if (!d) {
      return;
    }
    const call: Observable<SanctionDetail> = d.canDefend
      ? this.api.defend(d.id, this.text.trim(), this.file())
      : this.api.appeal(d.id, this.text.trim(), this.file());
    this.busy.set(true);
    this.error.set('');
    call.subscribe({
      next: (p) => {
        this.busy.set(false);
        this.p.set(p);
        this.text = '';
        this.file.set(null);
        this.snack.open('Enviado. Você recebe a decisão por e-mail.', 'OK', { duration: 6000 });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(toProblem(e).detail ?? problemMessage(e));
      },
    });
  }

  protected openFile(fileId: number): void {
    const d = this.p();
    if (d) {
      this.api.openFile(d.id, fileId);
    }
  }
}
