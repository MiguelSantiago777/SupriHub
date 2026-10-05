import { Component, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import { Territory, TERRITORIES } from '../../core/api/supplier.api';
import { Audience, TenderApi, TenderDetail, TenderInput } from '../../core/api/tender.api';
import { applyServerErrors } from '../../core/http/problem';
import { Municipality, SupplyLine } from '../../core/models';
import { AreaTabs } from '../shared/area-tabs';

function itemGroup(description = '', unit = 'un', quantity: number | null = null) {
  return new FormGroup({
    description: new FormControl(description, {
      nonNullable: true,
      validators: [Validators.required],
    }),
    unit: new FormControl(unit, { nonNullable: true, validators: [Validators.required] }),
    quantity: new FormControl<number | null>(quantity, [Validators.required, Validators.min(0.01)]),
  });
}

/** ISO (UTC) → valor de <input type="datetime-local"> no fuso do navegador. */
function toLocalInput(iso: string | null): string {
  if (!iso) {
    return '';
  }
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

@Component({
  selector: 'app-tender-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
    AreaTabs,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <p class="back"><a routerLink="/comissao/editais">← Editais</a></p>
        <span class="eyebrow">DFD-NATA · parte 5 (Anexo VI)</span>
        <h1>{{ id() ? 'Editar rascunho' : 'Nova oportunidade' }}</h1>
        <p>Salve como rascunho e publique quando estiver tudo certo.</p>
      </div>
    </header>
    <app-area-tabs />

    <form class="container page" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <section class="card">
        <h2>O que o Instituto precisa</h2>
        <mat-form-field class="full">
          <mat-label>Título</mat-label>
          <input
            matInput
            formControlName="title"
            maxlength="200"
            placeholder="Ex.: Cadeiras e mesas plásticas para o Festival 60+"
          />
          @if (form.controls.title.hasError('required')) {
            <mat-error>Dê um título.</mat-error>
          } @else if (form.controls.title.hasError('server')) {
            <mat-error>{{ form.controls.title.getError('server') }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field class="full">
          <mat-label>Descrição</mat-label>
          <textarea matInput formControlName="description" rows="4" maxlength="5000"></textarea>
          @if (form.controls.description.hasError('required')) {
            <mat-error>Descreva o que precisa.</mat-error>
          }
        </mat-form-field>
        <div class="row">
          <mat-form-field>
            <mat-label>Linha de fornecimento</mat-label>
            <mat-select formControlName="lineCode">
              @for (l of lines(); track l.code) {
                <mat-option [value]="l.code">{{ l.code }} · {{ l.shortName }}</mat-option>
              }
            </mat-select>
            @if (form.controls.lineCode.hasError('required')) {
              <mat-error>Escolha a linha.</mat-error>
            }
          </mat-form-field>
          <mat-form-field>
            <mat-label>A quem é dirigida</mat-label>
            <mat-select formControlName="audience">
              <mat-option value="ALL">Todos os credenciados da linha</mat-option>
              <mat-option value="WOMEN">Exclusiva a negócios liderados por mulheres</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <h3>Itens</h3>
        <div formArrayName="items" class="items">
          @for (g of items.controls; track g; let i = $index) {
            <div class="item" [formGroupName]="i">
              <span class="pos">{{ i + 1 }}</span>
              <mat-form-field class="desc">
                <mat-label>Descrição do item</mat-label>
                <input matInput formControlName="description" maxlength="500" />
              </mat-form-field>
              <mat-form-field class="unit">
                <mat-label>Unid.</mat-label>
                <input matInput formControlName="unit" maxlength="30" />
              </mat-form-field>
              <mat-form-field class="qty">
                <mat-label>Qtd.</mat-label>
                <input matInput type="number" min="0" step="0.01" formControlName="quantity" />
              </mat-form-field>
              <button
                mat-icon-button
                type="button"
                (click)="removeItem(i)"
                [disabled]="items.length === 1"
                [attr.aria-label]="'Remover item ' + (i + 1)"
              >
                <mat-icon>delete</mat-icon>
              </button>
            </div>
          }
        </div>
        <button mat-stroked-button type="button" (click)="addItem()">
          <mat-icon aria-hidden="true">add</mat-icon> Adicionar item
        </button>
      </section>

      <section class="card">
        <h2>Onde e quando</h2>
        <div class="row">
          <mat-form-field>
            <mat-label>Município de execução</mat-label>
            <mat-select formControlName="executionMunicipalityId">
              <mat-option [value]="null">—</mat-option>
              @for (m of municipalities(); track m.id) {
                <mat-option [value]="m.id">{{ m.name }}</mat-option>
              }
            </mat-select>
            <mat-hint>Define quem é T1 neste edital.</mat-hint>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Local</mat-label>
            <input matInput formControlName="location" maxlength="300" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Data(s) e horário(s)</mat-label>
            <input matInput formControlName="schedule" maxlength="300" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Prazo de execução/entrega</mat-label>
            <input matInput formControlName="executionDeadline" maxlength="300" />
          </mat-form-field>
        </div>
        <mat-form-field class="full">
          <mat-label>Requisitos (opcional)</mat-label>
          <textarea matInput formControlName="requirements" rows="2" maxlength="3000"></textarea>
        </mat-form-field>
        <mat-form-field class="full">
          <mat-label>Pagamento</mat-label>
          <input matInput formControlName="paymentTerms" maxlength="500" />
        </mat-form-field>
        <fieldset class="territories">
          <legend>Territórios convidados</legend>
          @for (t of territoryOptions; track t.value) {
            <mat-checkbox
              [checked]="territories().includes(t.value)"
              (change)="toggleTerritory(t.value, $event.checked)"
            >
              {{ t.label }}
            </mat-checkbox>
          }
        </fieldset>
      </section>

      <section class="card">
        <h2>Valor e prazo</h2>
        <div class="row">
          <mat-form-field>
            <mat-label>Valor estimado (R$)</mat-label>
            <input matInput type="number" min="0" step="0.01" formControlName="estimatedValue" />
            <mat-hint>Não aparece para os fornecedores.</mat-hint>
            @if (form.controls.estimatedValue.hasError('required')) {
              <mat-error>Informe o valor estimado.</mat-error>
            }
          </mat-form-field>
          @if (band(); as b) {
            <div class="band" [class.f1]="b.band === 'FAIXA_1'">
              <strong>{{ b.label }}</strong>
              <span>{{ b.selectionMode }}</span>
            </div>
          }
        </div>
        @if (band()?.band !== 'FAIXA_1') {
          <mat-form-field>
            <mat-label>Propostas até</mat-label>
            <input matInput type="datetime-local" formControlName="proposalDeadline" />
            <mat-hint>Mínimo: 3 dias úteis na Faixa 2 e 5 na Faixa 3.</mat-hint>
          </mat-form-field>
        } @else {
          <p class="alert alert-info">
            Faixa 1: ao publicar, o sistema convida o próximo credenciado da fila (rodízio), que tem
            2 dias úteis para aceitar com o preço.
          </p>
        }
      </section>

      @if (error()) {
        <p class="alert alert-error" role="alert">{{ error() }}</p>
      }
      <div class="form-actions bar">
        <a
          mat-stroked-button
          [routerLink]="id() ? ['/comissao/editais', id()] : '/comissao/editais'"
          >Cancelar</a
        >
        <button mat-flat-button type="submit" [disabled]="saving()">
          {{ saving() ? 'Salvando…' : 'Salvar rascunho' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .back {
      margin: 0 0 0.75rem !important;
    }
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
      display: grid;
      gap: 1.25rem;
    }
    h2 {
      font-size: 1.2rem;
      margin: 0 0 1rem;
    }
    h3 {
      font-size: 1rem;
      margin: 0.5rem 0 0.75rem;
    }
    .full {
      width: 100%;
    }
    .row {
      display: grid;
      gap: 0 1rem;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      align-items: start;
    }
    .items {
      display: grid;
      gap: 0.25rem;
      margin-bottom: 0.5rem;
    }
    .item {
      display: grid;
      grid-template-columns: 2rem 1fr 110px 120px auto;
      gap: 0 0.75rem;
      align-items: start;
    }
    @media (max-width: 700px) {
      .item {
        grid-template-columns: 2rem 1fr 1fr auto;
      }
      .item .desc {
        grid-column: 2 / -1;
      }
    }
    .pos {
      margin-top: 1rem;
      font-weight: 800;
      color: var(--sh-text-muted);
    }
    .territories {
      border: 0;
      padding: 0;
      margin: 0;
      display: grid;
    }
    .territories legend {
      font-weight: 700;
      margin-bottom: 0.25rem;
    }
    .band {
      display: flex;
      flex-direction: column;
      justify-content: center;
      padding: 0.6rem 1rem;
      border-radius: var(--sh-radius-sm);
      background: var(--sh-primary-soft);
      border: 1px solid #b8dcf1;
      min-height: 56px;
      box-sizing: border-box;
    }
    .band.f1 {
      background: var(--sh-amber-soft);
      border-color: #f0d48f;
    }
    .band span {
      font-size: 0.9rem;
      color: var(--sh-text-muted);
    }
    .bar {
      justify-content: flex-end;
    }
    .bar > * {
      flex: 0 0 auto;
    }
  `,
})
export class TenderFormPage implements OnInit {
  private readonly api = inject(TenderApi);
  private readonly catalog = inject(CatalogApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Parâmetro de rota `:id` (ausente na criação). */
  readonly id = input<string>();

  protected readonly territoryOptions = TERRITORIES;
  protected readonly lines = signal<SupplyLine[]>([]);
  protected readonly municipalities = signal<Municipality[]>([]);
  protected readonly territories = signal<Territory[]>(['T1']);
  protected readonly band = signal<{ band: string; label: string; selectionMode: string } | null>(
    null,
  );
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  private version = 0;

  protected readonly form = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    description: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    lineCode: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    audience: new FormControl<Audience>('ALL', { nonNullable: true }),
    executionMunicipalityId: new FormControl<number | null>(null),
    location: new FormControl(''),
    schedule: new FormControl(''),
    executionDeadline: new FormControl(''),
    requirements: new FormControl(''),
    paymentTerms: new FormControl('Pix ou transferência em até 30 dias após o recebimento'),
    estimatedValue: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(0.01),
    ]),
    proposalDeadline: new FormControl(''),
    items: new FormArray([itemGroup()]),
  });

  protected get items(): FormArray {
    return this.form.controls.items;
  }

  ngOnInit(): void {
    this.catalog.supplyLines().subscribe((l) => this.lines.set(l.filter((x) => x.active)));
    this.catalog.municipalities().subscribe((m) => {
      this.municipalities.set(m);
      // Nova oportunidade: Mesquita (sede do Instituto) como município de execução padrão.
      if (!this.id() && this.form.controls.executionMunicipalityId.value == null) {
        this.form.controls.executionMunicipalityId.setValue(
          m.find((x) => x.name === 'Mesquita')?.id ?? null,
        );
      }
    });
    this.form.controls.estimatedValue.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((v) =>
          v && v > 0 ? this.api.band(v).pipe(catchError(() => of(null))) : of(null),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((b) => this.band.set(b));
    const id = this.id();
    if (id) {
      this.api.get(Number(id)).subscribe((t) => this.fill(t));
    }
  }

  private fill(t: TenderDetail): void {
    this.version = t.version;
    this.items.clear();
    t.items.forEach((i) => this.items.push(itemGroup(i.description, i.unit, i.quantity)));
    this.form.patchValue({
      title: t.title,
      description: t.description,
      lineCode: t.line.code,
      audience: t.audience,
      executionMunicipalityId: t.executionMunicipalityId,
      location: t.location ?? '',
      schedule: t.schedule ?? '',
      executionDeadline: t.executionDeadline ?? '',
      requirements: t.requirements ?? '',
      paymentTerms: t.paymentTerms ?? '',
      estimatedValue: t.estimatedValue,
      proposalDeadline: toLocalInput(t.proposalDeadline),
    });
    this.territories.set(t.territories);
  }

  protected addItem(): void {
    this.items.push(itemGroup());
  }

  protected removeItem(i: number): void {
    this.items.removeAt(i);
  }

  protected toggleTerritory(t: Territory, checked: boolean): void {
    const set = new Set(this.territories());
    if (checked) {
      set.add(t);
    } else {
      set.delete(t);
    }
    this.territories.set(TERRITORIES.map((x) => x.value).filter((x) => set.has(x)));
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Preencha os campos obrigatórios.');
      return;
    }
    const v = this.form.getRawValue();
    const input: TenderInput = {
      title: v.title,
      description: v.description,
      lineCode: v.lineCode,
      executionMunicipalityId: v.executionMunicipalityId,
      location: v.location || null,
      schedule: v.schedule || null,
      executionDeadline: v.executionDeadline || null,
      requirements: v.requirements || null,
      paymentTerms: v.paymentTerms || null,
      audience: v.audience,
      estimatedValue: v.estimatedValue,
      proposalDeadline: v.proposalDeadline ? new Date(v.proposalDeadline).toISOString() : null,
      territories: this.territories(),
      items: v.items.map((i) => ({
        description: i.description ?? '',
        unit: i.unit ?? '',
        quantity: i.quantity ?? null,
      })),
    };
    this.saving.set(true);
    this.error.set('');
    const id = this.id();
    const call = id ? this.api.update(Number(id), input, this.version) : this.api.create(input);
    call.subscribe({
      next: (t) => {
        this.saving.set(false);
        void this.router.navigate(['/comissao/editais', t.id]);
      },
      error: (e: unknown) => {
        this.saving.set(false);
        this.error.set(applyServerErrors(this.form, e));
      },
    });
  }
}
