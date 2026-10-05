import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
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
import { Territory, TERRITORIES } from '../../core/api/supplier.api';
import {
  INVITATION_BADGE,
  Invitation,
  ProposalRow,
  TENDER_BADGE,
  TenderApi,
  TenderDetail,
} from '../../core/api/tender.api';
import { formatDate, formatDateTime, formatMoney, formatQuantity } from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

@Component({
  selector: 'app-tender-page',
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
  ],
  templateUrl: './tender.page.html',
  styleUrl: './tender.page.scss',
})
export class TenderPage {
  private readonly api = inject(TenderApi);
  private readonly snack = inject(MatSnackBar);

  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  protected readonly badge = TENDER_BADGE;
  protected readonly invBadge = INVITATION_BADGE;
  protected readonly territoryOptions = TERRITORIES;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly money = formatMoney;
  protected readonly qty = formatQuantity;

  protected readonly t = signal<TenderDetail | null>(null);
  protected readonly error = signal('');
  protected readonly busy = signal(false);
  protected readonly actionError = signal('');
  protected readonly issues = signal<{ field: string; message: string }[]>([]);
  protected readonly cancelling = signal(false);
  protected readonly rejecting = signal<number | null>(null);
  protected readonly expanded = signal<number | null>(null);
  protected readonly addTerritories = signal<Territory[]>([]);
  protected cancelReason = '';
  protected rejectReason = '';

  protected readonly activeInvitation = computed(
    () =>
      this.t()?.invitations.find((i) => i.status === 'INVITED' || i.status === 'ACCEPTED') ?? null,
  );

  protected readonly missingTerritories = computed(() =>
    TERRITORIES.filter((x) => !(this.t()?.territories ?? []).includes(x.value)),
  );

  protected readonly territoryLabels = computed(() =>
    TERRITORIES.filter((x) => (this.t()?.territories ?? []).includes(x.value))
      .map((x) => x.label)
      .join('; '),
  );

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.load(id));
    });
  }

  private load(id: number): void {
    this.api.get(id).subscribe({
      next: (t) => this.t.set(t),
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }

  private run(call: Observable<TenderDetail>, ok: string): void {
    this.busy.set(true);
    this.actionError.set('');
    this.issues.set([]);
    call.subscribe({
      next: (t) => {
        this.busy.set(false);
        this.t.set(t);
        this.cancelling.set(false);
        this.rejecting.set(null);
        this.addTerritories.set([]);
        this.snack.open(ok, 'OK', { duration: 5000 });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const problem = toProblem(e);
        this.actionError.set(problem.detail ?? problemMessage(e));
        this.issues.set(problem.errors ?? []);
      },
    });
  }

  protected publish(): void {
    const t = this.t();
    if (t) {
      this.run(
        this.api.publish(t.id, t.version),
        t.band === 'FAIXA_1'
          ? 'Publicado. O primeiro da fila do rodízio foi convidado.'
          : 'Publicado. Os fornecedores elegíveis foram avisados por e-mail.',
      );
    }
  }

  protected cancel(): void {
    const t = this.t();
    if (t && this.cancelReason.trim()) {
      this.run(
        this.api.cancel(t.id, this.cancelReason.trim(), t.version),
        'Oportunidade cancelada.',
      );
    }
  }

  protected confirm(inv: Invitation): void {
    this.run(this.api.confirm(inv.id), 'Seleção confirmada.');
  }

  protected rejectPrice(inv: Invitation): void {
    if (this.rejectReason.trim()) {
      this.run(
        this.api.rejectPrice(inv.id, this.rejectReason.trim()),
        'Preço recusado. O próximo da fila foi convidado.',
      );
    }
  }

  protected toggleAdd(t: Territory, checked: boolean): void {
    const set = new Set(this.addTerritories());
    if (checked) {
      set.add(t);
    } else {
      set.delete(t);
    }
    this.addTerritories.set([...set]);
  }

  protected expand(): void {
    const t = this.t();
    if (t && this.addTerritories().length) {
      this.run(
        this.api.expand(t.id, [...t.territories, ...this.addTerritories()], t.version),
        'Território ampliado.',
      );
    }
  }

  protected openFile(p: ProposalRow): void {
    const t = this.t();
    if (t) {
      this.api.openProposalFile(t.id, p.id);
    }
  }

  protected toggleRow(id: number): void {
    this.expanded.set(this.expanded() === id ? null : id);
  }

  protected price(p: ProposalRow, itemId: number): number | null {
    return p.unitPrices[String(itemId)] ?? null;
  }
}
