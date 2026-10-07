import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { CatalogApi } from '../../core/api/catalog.api';
import { ContractApi } from '../../core/api/contract.api';
import { AccreditedSupplier, AccreditedSupplierApi } from '../../core/api/supplier.api';
import { problemMessage, toProblem } from '../../core/http/problem';
import { Municipality, SupplyLine } from '../../core/models';
import { AreaTabs } from '../shared/area-tabs';

interface ItemRow {
  description: string;
  unit: string;
  quantity: number | null;
}

/**
 * Contratação de urgência (Política art. 13, § 3º; Manual 4.3): o gestor contrata direto um
 * credenciado da linha, com a justificativa no DFD. A Comissão toma ciência em 2 dias úteis.
 */
@Component({
  selector: 'app-urgent-page',
  imports: [
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    AreaTabs,
  ],
  template: `
    <header class="page-header">
      <div class="container">
        <p class="back"><a routerLink="/contratos">← Ordens de fornecimento</a></p>
        <span class="eyebrow">Contratação</span>
        <h1>Contratação de urgência</h1>
        <p>
          Só para urgência justificada: risco à segurança dos participantes, fornecedor que cancelou
          às vésperas ou fato imprevisível. Contrata-se direto um credenciado ativo da linha, sem
          rodízio nem cotação. A Comissão toma ciência em até 2 dias úteis e acompanha a frequência:
          urgência não pode virar rotina.
        </p>
      </div>
    </header>
    <app-area-tabs />

    <div class="container page">
      <form class="card form" (ngSubmit)="submit()">
        <h2>1. O que precisa (DFD)</h2>
        <div class="two">
          <mat-form-field>
            <mat-label>Linha de fornecimento</mat-label>
            <mat-select [(ngModel)]="lineCode" name="lineCode" (selectionChange)="loadSuppliers()">
              @for (l of lines(); track l.code) {
                <mat-option [value]="l.code">{{ l.code }} · {{ l.shortName }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Título</mat-label>
            <input matInput [(ngModel)]="title" name="title" maxlength="200" required />
          </mat-form-field>
        </div>
        <mat-form-field>
          <mat-label>Descrição</mat-label>
          <textarea
            matInput
            rows="3"
            [(ngModel)]="description"
            name="description"
            maxlength="5000"
          ></textarea>
        </mat-form-field>
        <div class="three">
          <mat-form-field>
            <mat-label>Município de execução</mat-label>
            <mat-select [(ngModel)]="municipalityId" name="municipalityId">
              @for (m of municipalities(); track m.id) {
                <mat-option [value]="m.id">{{ m.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Local</mat-label>
            <input matInput [(ngModel)]="location" name="location" maxlength="300" />
          </mat-form-field>
          <mat-form-field>
            <mat-label>Data e horário</mat-label>
            <input matInput [(ngModel)]="schedule" name="schedule" maxlength="300" />
          </mat-form-field>
        </div>

        <h3>Itens</h3>
        @for (it of items; track $index) {
          <div class="item">
            <mat-form-field class="grow">
              <mat-label>Descrição do item</mat-label>
              <input matInput [(ngModel)]="it.description" [name]="'d' + $index" maxlength="500" />
            </mat-form-field>
            <mat-form-field class="small">
              <mat-label>Unidade</mat-label>
              <input matInput [(ngModel)]="it.unit" [name]="'u' + $index" maxlength="30" />
            </mat-form-field>
            <mat-form-field class="small">
              <mat-label>Quantidade</mat-label>
              <input
                matInput
                type="number"
                min="0"
                [(ngModel)]="it.quantity"
                [name]="'q' + $index"
              />
            </mat-form-field>
            @if (items.length > 1) {
              <button
                mat-icon-button
                type="button"
                (click)="items.splice($index, 1)"
                aria-label="Remover item"
              >
                <mat-icon>delete</mat-icon>
              </button>
            }
          </div>
        }
        <button
          mat-button
          type="button"
          (click)="items.push({ description: '', unit: 'un', quantity: 1 })"
        >
          <mat-icon aria-hidden="true">add</mat-icon> Incluir item
        </button>

        <h2>2. Credenciado e valor</h2>
        <div class="two">
          <mat-form-field>
            <mat-label>Credenciado ativo da linha</mat-label>
            <mat-select [(ngModel)]="applicationId" name="applicationId" [disabled]="!lineCode">
              @for (s of suppliers(); track s.id) {
                <mat-option [value]="s.id">{{ s.name }} · {{ s.municipalityName }}</mat-option>
              } @empty {
                <mat-option disabled>
                  {{
                    lineCode ? 'Nenhum credenciado ativo nesta linha' : 'Escolha a linha primeiro'
                  }}
                </mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Valor total (R$)</mat-label>
            <input matInput type="number" min="0" step="0.01" [(ngModel)]="value" name="value" />
            <mat-hint>Preço combinado com o credenciado; confira no Banco de Preços.</mat-hint>
          </mat-form-field>
        </div>

        <h2>3. Justificativa da urgência</h2>
        <mat-form-field>
          <mat-label>Por que não dá para fazer rodízio ou cotação?</mat-label>
          <textarea
            matInput
            rows="4"
            [(ngModel)]="justification"
            name="justification"
            maxlength="3000"
          ></textarea>
          <mat-hint
            >Fica registrada no DFD e vai para a Comissão (mínimo de 20 caracteres).</mat-hint
          >
        </mat-form-field>

        @if (error()) {
          <div class="alert alert-error" role="alert">
            <p>
              <strong>{{ error() }}</strong>
            </p>
            @if (issues().length) {
              <ul>
                @for (i of issues(); track i.field) {
                  <li>{{ i.message }}</li>
                }
              </ul>
            }
          </div>
        }
        <div class="btns">
          <a mat-button routerLink="/contratos">Cancelar</a>
          <button mat-flat-button type="submit" [disabled]="busy()">
            <mat-icon aria-hidden="true">priority_high</mat-icon> Contratar com urgência
          </button>
        </div>
      </form>
    </div>
  `,
  styles: `
    .back {
      margin: 0 0 0.75rem !important;
    }
    .page {
      padding-top: 1.5rem;
      padding-bottom: 2rem;
    }
    .form {
      display: grid;
      gap: 0.25rem;
      max-width: 960px;
    }
    h2 {
      font-size: 1.2rem;
      margin: 1rem 0 0.5rem;
    }
    h2:first-child {
      margin-top: 0;
    }
    h3 {
      font-size: 1rem;
      margin: 0.5rem 0;
    }
    .two,
    .three {
      display: grid;
      gap: 0 1rem;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    }
    .item {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      gap: 0 0.75rem;
    }
    .item .grow {
      flex: 1 1 280px;
    }
    .item .small {
      flex: 0 1 140px;
    }
    .btns {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 1rem;
    }
  `,
})
export class UrgentPage implements OnInit {
  private readonly api = inject(ContractApi);
  private readonly catalog = inject(CatalogApi);
  private readonly accredited = inject(AccreditedSupplierApi);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  protected readonly lines = signal<SupplyLine[]>([]);
  protected readonly municipalities = signal<Municipality[]>([]);
  protected readonly suppliers = signal<AccreditedSupplier[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly issues = signal<{ field: string; message: string }[]>([]);

  protected lineCode = '';
  protected title = '';
  protected description = '';
  protected municipalityId: number | null = null;
  protected location = '';
  protected schedule = '';
  protected items: ItemRow[] = [{ description: '', unit: 'un', quantity: 1 }];
  protected applicationId: number | null = null;
  protected value: number | null = null;
  protected justification = '';

  ngOnInit(): void {
    this.catalog.supplyLines().subscribe((l) => this.lines.set(l.filter((x) => x.active)));
    this.catalog.municipalities().subscribe((m) => this.municipalities.set(m));
  }

  protected loadSuppliers(): void {
    this.applicationId = null;
    this.suppliers.set([]);
    if (this.lineCode) {
      this.accredited
        .search(this.lineCode, null, '', 0)
        .subscribe((p) => this.suppliers.set(p.content));
    }
  }

  protected submit(): void {
    if (!this.applicationId || !this.value) {
      this.error.set('Escolha o credenciado e informe o valor.');
      this.issues.set([]);
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.issues.set([]);
    this.api
      .createUrgent({
        title: this.title.trim(),
        description: this.description.trim(),
        lineCode: this.lineCode,
        executionMunicipalityId: this.municipalityId,
        location: this.location.trim() || null,
        schedule: this.schedule.trim() || null,
        value: this.value,
        applicationId: this.applicationId,
        justification: this.justification.trim(),
        items: this.items.map((i) => ({
          description: i.description.trim(),
          unit: i.unit.trim(),
          quantity: i.quantity ?? 0,
        })),
      })
      .subscribe({
        next: (o) => {
          this.busy.set(false);
          this.snack.open('Contratação registrada. Prepare e emita a ordem.', 'OK', {
            duration: 6000,
          });
          void this.router.navigate(['/contratos', o.id]);
        },
        error: (e: unknown) => {
          this.busy.set(false);
          const p = toProblem(e);
          this.error.set(p.detail ?? problemMessage(e));
          this.issues.set(p.errors ?? []);
        },
      });
  }
}
