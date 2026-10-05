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
import { SupplierApi, SupplierType, TERRITORIES } from '../../core/api/supplier.api';
import {
  Opportunity,
  OpportunityApi,
  ProposalInput,
  ProposalMode,
} from '../../core/api/tender.api';
import {
  formatDate,
  formatDateTime,
  formatMoney,
  formatQuantity,
  formatSize,
} from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { AreaTabs } from '../shared/area-tabs';

interface Method {
  mode: ProposalMode;
  icon: string;
  title: string;
  text: string;
}

const METHODS: Method[] = [
  {
    mode: 'MODEL',
    icon: 'description',
    title: 'Baixar o modelo pronto',
    text: 'Baixe a proposta (Anexo VII) em Word já com seus dados e os itens. Preencha os valores, assine e envie de volta — pode ser foto.',
  },
  {
    mode: 'ONLINE',
    icon: 'edit_square',
    title: 'Preencher aqui',
    text: 'Digite o preço de cada item e o sistema calcula o total. Não precisa de arquivo.',
  },
  {
    mode: 'OWN_FILE',
    icon: 'upload_file',
    title: 'Enviar minha proposta',
    text: 'Já tem proposta no modelo da sua empresa? Envie o arquivo (PDF ou Word) e informe o valor total.',
  },
];

const ACCEPT = '.pdf,.docx,.jpg,.jpeg,.png';
const MAX_MB = 10;

@Component({
  selector: 'app-opportunity-page',
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
  templateUrl: './opportunity.page.html',
  styleUrl: './opportunity.page.scss',
})
export class OpportunityPage {
  private readonly api = inject(OpportunityApi);
  private readonly supplierApi = inject(SupplierApi);
  private readonly snack = inject(MatSnackBar);

  /** Parâmetro de rota `:id`. */
  readonly id = input.required<string>();

  protected readonly methods = METHODS;
  protected readonly accept = ACCEPT;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly money = formatMoney;
  protected readonly qty = formatQuantity;
  protected readonly size = formatSize;

  protected readonly op = signal<Opportunity | null>(null);
  protected readonly supplierType = signal<SupplierType | null>(null);
  protected readonly error = signal('');
  protected readonly editing = signal(false);
  protected readonly mode = signal<ProposalMode | null>(null);
  protected readonly busy = signal(false);
  protected readonly formError = signal('');
  protected readonly fieldErrors = signal<Record<string, string>>({});
  protected readonly declining = signal(false);

  // Campos do formulário
  protected readonly prices = signal<Record<number, number | null>>({});
  protected totalValue: number | null = null;
  protected executionDeadline = '';
  protected validityDays = 30;
  protected specialConditions = '';
  protected notes = '';
  protected declaration = false;
  protected declineReason = '';
  protected readonly file = signal<File | null>(null);

  /** Modo sugerido: MEI e autônomo → modelo pronto; empresas → preencher aqui. */
  protected readonly recommended = computed<ProposalMode>(() =>
    this.supplierType() === 'MEI' || this.supplierType() === 'INDIVIDUAL' ? 'MODEL' : 'ONLINE',
  );

  protected readonly onlineTotal = computed(() => {
    const o = this.op();
    if (!o) {
      return 0;
    }
    const p = this.prices();
    return o.items.reduce((sum, i) => sum + (p[i.id] ?? 0) * i.quantity, 0);
  });

  protected readonly showForm = computed(() => {
    const o = this.op();
    return !!o && o.canSubmit && (!o.proposal || this.editing());
  });

  protected readonly territoryLabels = computed(() => {
    const o = this.op();
    return o
      ? TERRITORIES.filter((t) => o.territories.includes(t.value))
          .map((t) => t.label)
          .join('; ')
      : '';
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.load(id));
    });
    this.supplierApi.get().subscribe({ next: (a) => this.supplierType.set(a.supplierType) });
  }

  private load(id: number): void {
    this.api.get(id).subscribe({
      next: (o) => this.set(o),
      error: (e: unknown) => this.error.set(problemMessage(e)),
    });
  }

  private set(o: Opportunity): void {
    this.op.set(o);
    this.validityDays = Math.max(this.validityDays, o.minValidityDays);
    if (o.proposal) {
      const p: Record<number, number | null> = {};
      for (const [k, v] of Object.entries(o.proposal.unitPrices)) {
        p[Number(k)] = v;
      }
      this.prices.set(p);
      this.totalValue = o.proposal.totalValue;
      this.executionDeadline = o.proposal.executionDeadline ?? '';
      this.validityDays = o.proposal.validityDays;
      this.specialConditions = o.proposal.specialConditions ?? '';
      this.notes = o.proposal.notes ?? '';
    }
  }

  protected choose(mode: ProposalMode): void {
    this.mode.set(mode);
    this.formError.set('');
    this.fieldErrors.set({});
    queueMicrotask(() =>
      document.getElementById('proposal-form')?.scrollIntoView({ behavior: 'smooth' }),
    );
  }

  protected setPrice(itemId: number, value: string): void {
    const n = value === '' ? null : Number(value);
    this.prices.update((p) => ({ ...p, [itemId]: n }));
  }

  protected pickFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    if (f && f.size > MAX_MB * 1024 * 1024) {
      input.value = '';
      this.snack.open(`O arquivo tem mais de ${MAX_MB} MB.`, 'OK', { duration: 5000 });
      return;
    }
    this.file.set(f);
  }

  protected downloadModel(): void {
    const o = this.op();
    if (o) {
      this.api.downloadModel(o.id, o.number);
    }
  }

  protected edit(): void {
    this.editing.set(true);
    this.mode.set(this.op()?.proposal?.mode ?? null);
  }

  protected send(): void {
    const o = this.op();
    const mode = this.mode();
    if (!o || !mode) {
      return;
    }
    const unitPrices: Record<number, number> = {};
    for (const [k, v] of Object.entries(this.prices())) {
      if (v != null && !Number.isNaN(v)) {
        unitPrices[Number(k)] = v;
      }
    }
    const input: ProposalInput = {
      mode,
      unitPrices: mode === 'ONLINE' ? unitPrices : {},
      totalValue: mode === 'ONLINE' ? null : this.totalValue,
      executionDeadline: this.executionDeadline || null,
      validityDays: Number(this.validityDays),
      specialConditions: this.specialConditions || null,
      notes: this.notes || null,
      declarationAccepted: this.declaration,
    };
    this.busy.set(true);
    this.formError.set('');
    this.fieldErrors.set({});
    this.api.submit(o.id, input, mode === 'ONLINE' ? null : this.file()).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.editing.set(false);
        this.file.set(null);
        this.declaration = false;
        this.set(updated);
        this.snack.open(
          o.band === 'FAIXA_1'
            ? 'Convite aceito. A Comissão vai conferir o preço.'
            : 'Proposta enviada! Você pode alterá-la até o prazo.',
          'OK',
          { duration: 6000 },
        );
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        const p = toProblem(e);
        const errs: Record<string, string> = {};
        (p.errors ?? []).forEach((x) => (errs[x.field] = x.message));
        this.fieldErrors.set(errs);
        this.formError.set(p.detail ?? problemMessage(e));
      },
    });
  }

  protected withdraw(): void {
    const o = this.op();
    if (!o) {
      return;
    }
    this.busy.set(true);
    this.api.withdraw(o.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.snack.open('Proposta retirada.', 'OK', { duration: 4000 });
        this.load(o.id);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.snack.open(problemMessage(e), 'OK', { duration: 6000 });
      },
    });
  }

  protected decline(): void {
    const o = this.op();
    if (!o) {
      return;
    }
    this.busy.set(true);
    this.api.decline(o.id, this.declineReason).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.declining.set(false);
        this.set(updated);
        this.snack.open('Convite recusado. A vez passou ao próximo credenciado.', 'OK', {
          duration: 5000,
        });
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.snack.open(problemMessage(e), 'OK', { duration: 6000 });
      },
    });
  }

  protected openMyFile(): void {
    const o = this.op();
    if (o) {
      this.api.openMyFile(o.id);
    }
  }

  protected methodTitle(mode: ProposalMode): string {
    return METHODS.find((m) => m.mode === mode)?.title ?? '';
  }
}
