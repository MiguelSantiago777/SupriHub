import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { Dossier, DossierApi } from '../../core/api/control.api';
import { AuthService } from '../../core/auth/auth.service';
import { formatDateTime } from '../../core/format';
import { problemMessage } from '../../core/http/problem';

const ICON: Record<string, string> = {
  OK: 'check_circle',
  PENDING: 'radio_button_unchecked',
  NOT_APPLICABLE: 'remove',
};

/** Checklist do Dossiê da Contratação (Anexo XIII) na tela da ordem. */
@Component({
  selector: 'app-dossier-panel',
  imports: [
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  template: `
    <section class="card" aria-labelledby="dossier-title">
      <div class="title-row">
        <h2 id="dossier-title">
          <mat-icon aria-hidden="true">inventory_2</mat-icon> Dossiê (Anexo XIII)
        </h2>
        @if (d(); as x) {
          <span class="badge" [class]="x.complete ? 'badge-success' : 'badge-warning'">
            {{ x.complete ? 'Completo' : x.pending + ' pendência(s)' }}
          </span>
        }
      </div>
      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      }
      @if (d(); as x) {
        <ol class="items">
          @for (i of x.items; track i.no) {
            <li [class]="i.state">
              <mat-icon aria-hidden="true">{{ icon[i.state] }}</mat-icon>
              <div class="text">
                <span
                  >{{ i.no }}. {{ i.label }}
                  @if (i.state === 'NOT_APPLICABLE') {
                    <span class="muted">— não se aplica</span>
                  }
                </span>
                @if (i.detail) {
                  <small class="muted">{{ i.detail }}</small>
                }
                @if (i.manual && canCheck()) {
                  <div class="manual">
                    <mat-checkbox
                      [checked]="i.state === 'OK'"
                      (change)="check(i.no, $event.checked)"
                      [disabled]="busy()"
                      >Conferido</mat-checkbox
                    >
                    <input
                      class="note"
                      [attr.aria-label]="'Observação do item ' + i.no"
                      placeholder="observação (opcional)"
                      maxlength="500"
                      [(ngModel)]="notes[i.no]"
                    />
                  </div>
                }
              </div>
            </li>
          }
        </ol>

        @if (x.reviews.length) {
          <h3>Revisões do Controle Interno</h3>
          <ul class="reviews">
            @for (r of x.reviews; track r.id) {
              <li>
                {{ dateTime(r.reviewedAt) }} —
                <strong>{{ r.complete ? 'sem pendência' : 'com pendência' }}</strong>
                @if (r.note) {
                  · {{ r.note }}
                }
              </li>
            }
          </ul>
        }
        @if (canReview()) {
          <div class="review">
            <mat-form-field class="grow">
              <mat-label>Observação da revisão</mat-label>
              <input matInput [(ngModel)]="reviewNote" maxlength="2000" />
            </mat-form-field>
            <button mat-flat-button type="button" [disabled]="busy()" (click)="review()">
              Registrar revisão
            </button>
          </div>
        }
      }
    </section>
  `,
  styles: `
    .title-row {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
      h2 {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 1.2rem;
        margin: 0;
        mat-icon {
          color: var(--sh-primary);
        }
      }
    }
    .items {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.5rem;
      li {
        display: flex;
        gap: 0.5rem;
        align-items: flex-start;
      }
      li.OK mat-icon {
        color: var(--sh-green);
      }
      li.PENDING mat-icon {
        color: var(--sh-amber);
      }
      li.NOT_APPLICABLE {
        color: var(--sh-text-muted);
      }
    }
    .text {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
    }
    .manual {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.25rem 0.75rem;
    }
    .note {
      font: inherit;
      font-size: 0.9rem;
      padding: 0.35rem 0.5rem;
      border: 1px solid var(--sh-border-strong);
      border-radius: 8px;
      min-width: 220px;
    }
    h3 {
      font-size: 1rem;
      margin: 1rem 0 0.35rem;
    }
    .reviews {
      margin: 0;
      padding-left: 1.2rem;
    }
    .review {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: flex-start;
      margin-top: 1rem;
    }
    .grow {
      flex: 1 1 260px;
    }
  `,
})
export class DossierPanel {
  private readonly api = inject(DossierApi);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);

  readonly orderId = input.required<number>();

  protected readonly icon = ICON;
  protected readonly dateTime = formatDateTime;
  protected readonly d = signal<Dossier | null>(null);
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly canCheck = computed(() =>
    this.auth.hasRole('COMMISSION', 'DEMAND_MANAGER', 'FINANCE', 'ADMIN'),
  );
  protected readonly canReview = computed(() => this.auth.hasRole('INTERNAL_CONTROL', 'ADMIN'));
  protected notes: Record<number, string> = {};
  protected reviewNote = '';

  constructor() {
    effect(() => {
      const id = this.orderId();
      untracked(() =>
        this.api.get(id).subscribe({
          next: (d) => this.set(d),
          error: (e: unknown) => this.error.set(problemMessage(e)),
        }),
      );
    });
  }

  private set(d: Dossier): void {
    this.d.set(d);
    for (const i of d.items) {
      if (i.manual && this.notes[i.no] === undefined) {
        this.notes[i.no] = i.detail ?? '';
      }
    }
  }

  private run(call: Observable<Dossier>, ok: string): void {
    this.busy.set(true);
    this.error.set('');
    call.subscribe({
      next: (d) => {
        this.busy.set(false);
        this.set(d);
        this.snack.open(ok, 'OK', { duration: 3000 });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(problemMessage(e));
      },
    });
  }

  protected check(no: number, checked: boolean): void {
    this.run(
      this.api.check(this.orderId(), no, checked, (this.notes[no] ?? '').trim() || null),
      'Dossiê atualizado.',
    );
  }

  protected review(): void {
    this.run(
      this.api.review(this.orderId(), this.reviewNote.trim() || null),
      'Revisão registrada.',
    );
    this.reviewNote = '';
  }
}
