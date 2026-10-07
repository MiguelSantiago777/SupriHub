import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import {
  ACCOUNT_TYPES,
  Application,
  RACES,
  STATUS_BADGE,
  TERRITORIES,
} from '../../core/api/supplier.api';
import {
  formatCep,
  formatCpf,
  formatDate,
  formatDateTime,
  formatDocument,
  formatPhone,
  formatSize,
  todayIso,
} from '../../core/format';

/** Requerimento em modo leitura: dados do Anexo II, documentos e andamento. */
@Component({
  selector: 'app-application-view',
  imports: [MatButtonModule, MatIconModule],
  template: `
    @let a = app();
    <section class="card block" aria-labelledby="v-ident">
      <h2 id="v-ident"><mat-icon aria-hidden="true">badge</mat-icon> Identificação e contato</h2>
      <dl class="grid">
        <div>
          <dt>Tipo</dt>
          <dd>{{ a.supplierTypeLabel || '—' }}</dd>
        </div>
        <div>
          <dt>{{ a.supplierType === 'INDIVIDUAL' ? 'Nome' : 'Razão social' }}</dt>
          <dd>{{ a.legalName || '—' }}</dd>
        </div>
        @if (a.tradeName) {
          <div>
            <dt>Nome fantasia</dt>
            <dd>{{ a.tradeName }}</dd>
          </div>
        }
        <div>
          <dt>{{ a.supplierType === 'INDIVIDUAL' ? 'CPF' : 'CNPJ' }}</dt>
          <dd>{{ fmtDoc(a.documentNumber) || '—' }}</dd>
        </div>
        @if (a.birthDate) {
          <div>
            <dt>Nascimento</dt>
            <dd>{{ fmtDate(a.birthDate) }}</dd>
          </div>
        }
        <div class="wide">
          <dt>Endereço</dt>
          <dd>
            {{ address() || '—' }}
          </dd>
        </div>
        <div>
          <dt>Telefone (WhatsApp)</dt>
          <dd>{{ fmtPhone(a.phone) || '—' }}</dd>
        </div>
        <div>
          <dt>E-mail</dt>
          <dd>{{ a.contactEmail || '—' }}</dd>
        </div>
        @if (a.website) {
          <div>
            <dt>Site / redes</dt>
            <dd>{{ a.website }}</dd>
          </div>
        }
        @if (a.representativeName) {
          <div>
            <dt>Representante</dt>
            <dd>
              {{ a.representativeName }}
              @if (a.representativeRole) {
                <span class="muted">({{ a.representativeRole }})</span>
              }
              <br /><span class="muted">CPF {{ fmtCpf(a.representativeCpf) }}</span>
            </dd>
          </div>
        }
      </dl>
    </section>

    <section class="card block" aria-labelledby="v-linhas">
      <h2 id="v-linhas"><mat-icon aria-hidden="true">category</mat-icon> Linhas e territórios</h2>
      <ul class="chips" aria-label="Linhas de fornecimento">
        @for (l of a.lines; track l.code) {
          <li>
            <strong>{{ l.code }}</strong> {{ l.shortName }}
          </li>
        } @empty {
          <li class="muted">Nenhuma linha escolhida</li>
        }
      </ul>
      @if (a.linesDescription) {
        <p class="pre">{{ a.linesDescription }}</p>
      }
      <dl class="grid">
        <div class="wide">
          <dt>Territórios de atendimento</dt>
          <dd>{{ territories() || '—' }}</dd>
        </div>
        @if (a.territoriesNote) {
          <div class="wide">
            <dt>Municípios</dt>
            <dd>{{ a.territoriesNote }}</dd>
          </div>
        }
      </dl>
    </section>

    <section class="card block" aria-labelledby="v-banco">
      <h2 id="v-banco">
        <mat-icon aria-hidden="true">account_balance</mat-icon> Dados para pagamento
      </h2>
      <dl class="grid">
        <div>
          <dt>Banco</dt>
          <dd>{{ a.bankName || '—' }}</dd>
        </div>
        <div>
          <dt>Agência</dt>
          <dd>{{ a.bankBranch || '—' }}</dd>
        </div>
        <div>
          <dt>Conta</dt>
          <dd>
            {{ a.bankAccount || '—' }} <span class="muted">{{ accountType() }}</span>
          </dd>
        </div>
        <div>
          <dt>Chave Pix</dt>
          <dd>{{ a.pixKey || '—' }}</dd>
        </div>
        <div class="wide">
          <dt>Titular</dt>
          <dd>{{ a.accountHolder || '—' }}</dd>
        </div>
      </dl>
    </section>

    @if (showEquity()) {
      <section class="card block" aria-labelledby="v-eq">
        <h2 id="v-eq">
          <mat-icon aria-hidden="true">diversity_3</mat-icon> Equidade (facultativo)
        </h2>
        <p class="muted small">Usado só no desempate e em estatísticas; nunca publicado.</p>
        <dl class="grid">
          <div>
            <dt>Negócio liderado por mulher</dt>
            <dd>{{ a.womanLed ? 'Sim' : 'Não' }}</dd>
          </div>
          <div>
            <dt>Raça/cor</dt>
            <dd>{{ race() || 'Não informado' }}</dd>
          </div>
          <div>
            <dt>CadÚnico</dt>
            <dd>{{ a.cadUnico ? 'Sim — NIS ' + (a.nis || '') : 'Não' }}</dd>
          </div>
        </dl>
      </section>
    }

    <section class="card block" aria-labelledby="v-docs">
      <h2 id="v-docs"><mat-icon aria-hidden="true">folder_open</mat-icon> Documentos</h2>
      <ul class="docs">
        @for (item of a.checklist; track item.type) {
          <li [class.missing]="!item.document">
            <mat-icon aria-hidden="true">{{
              item.document ? 'description' : item.requirement === 'REQUIRED' ? 'error' : 'remove'
            }}</mat-icon>
            <div class="doc-text">
              <span>{{ item.label }}</span>
              @if (item.document) {
                <small class="muted"
                  >{{ item.document.filename }} · {{ size(item.document.sizeBytes) }} · enviado em
                  {{ dateTime(item.document.uploadedAt) }}</small
                >
                @if (item.certificate) {
                  <small [class.expired]="isExpired(item.document.validUntil)">
                    @if (item.document.validUntil) {
                      Válida até <strong>{{ fmtDate(item.document.validUntil) }}</strong>
                      @if (item.document.verifiedAt) {
                        · conferida pela Comissão
                      }
                    } @else {
                      Validade não informada
                    }
                  </small>
                }
              } @else {
                <small class="muted">{{
                  item.requirement === 'REQUIRED' ? 'Não enviado' : 'Opcional — não enviado'
                }}</small>
              }
            </div>
            @if (item.document) {
              <button
                mat-stroked-button
                type="button"
                (click)="openDocument.emit(item.document.id)"
              >
                <mat-icon aria-hidden="true">open_in_new</mat-icon> Abrir
              </button>
            }
          </li>
        }
      </ul>
      @if (a.declarationsAcceptedAt) {
        <p class="declared">
          <mat-icon aria-hidden="true">verified</mat-icon>
          Declarações e termo de adesão (Anexos III e V{{ a.womanLed ? ' e IV' : '' }}) aceitos em
          {{ dateTime(a.declarationsAcceptedAt) }}.
        </p>
      }
    </section>

    <section class="card block" aria-labelledby="v-hist">
      <h2 id="v-hist"><mat-icon aria-hidden="true">history</mat-icon> Andamento</h2>
      <ol class="timeline">
        @for (h of reversedHistory(); track $index) {
          <li>
            <span class="badge" [class]="badge[h.status]">{{ h.statusLabel }}</span>
            <span class="muted when">{{ dateTime(h.occurredAt) }} · {{ h.actor }}</span>
            @if (h.message) {
              <p class="pre">{{ h.message }}</p>
            }
          </li>
        }
      </ol>
    </section>
  `,
  styles: `
    :host {
      display: grid;
      gap: 1rem;
    }
    h2 {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 1.2rem;
      margin: 0 0 1rem;
    }
    h2 mat-icon {
      color: var(--sh-primary);
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 1rem 1.5rem;
      margin: 0;
    }
    .grid .wide {
      grid-column: 1 / -1;
    }
    dt {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--sh-text-muted);
    }
    dd {
      margin: 0.15rem 0 0;
      overflow-wrap: anywhere;
    }
    .small {
      font-size: 0.9rem;
      margin-top: -0.5rem;
    }
    .chips {
      list-style: none;
      padding: 0;
      margin: 0 0 1rem;
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .chips li {
      padding: 0.3rem 0.75rem;
      border-radius: 999px;
      background: var(--sh-primary-soft);
      color: var(--sh-navy);
      font-size: 0.92rem;
    }
    .pre {
      white-space: pre-line;
    }
    .docs {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.5rem;
    }
    .docs li {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.75rem;
      border: 1px solid var(--sh-border);
      border-radius: var(--sh-radius-sm);
    }
    .docs li > mat-icon {
      color: var(--sh-green);
      flex: 0 0 auto;
    }
    .docs li.missing > mat-icon {
      color: var(--sh-text-muted);
    }
    .doc-text {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .expired {
      color: var(--sh-error);
      font-weight: 700;
    }
    .declared {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 1rem 0 0;
      color: var(--sh-green);
      font-weight: 700;
    }
    .timeline {
      list-style: none;
      padding: 0 0 0 1rem;
      margin: 0;
      border-left: 2px solid var(--sh-border);
      display: grid;
      gap: 1rem;
    }
    .timeline li {
      position: relative;
    }
    .timeline li::before {
      content: '';
      position: absolute;
      left: calc(-1rem - 6px);
      top: 0.45rem;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: var(--sh-primary);
    }
    .when {
      margin-left: 0.5rem;
      font-size: 0.88rem;
    }
    .timeline p {
      margin: 0.4rem 0 0;
    }
  `,
})
export class ApplicationView {
  readonly app = input.required<Application>();
  /** Equidade é dado sensível: só a própria pessoa e a Comissão veem. */
  readonly showEquity = input(true);
  readonly openDocument = output<number>();

  protected readonly badge = STATUS_BADGE;
  protected readonly fmtDoc = formatDocument;
  protected readonly fmtCpf = formatCpf;
  protected readonly fmtPhone = formatPhone;
  protected readonly fmtDate = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly size = formatSize;

  protected readonly address = computed(() => {
    const a = this.app();
    const parts = [
      [a.street, a.addressNumber].filter(Boolean).join(', '),
      a.addressComplement,
      a.district,
      a.municipalityName ? `${a.municipalityName}/RJ` : null,
      a.cep ? `CEP ${formatCep(a.cep)}` : null,
    ];
    return parts.filter(Boolean).join(' · ');
  });

  protected readonly territories = computed(() =>
    TERRITORIES.filter((t) => this.app().territories.includes(t.value))
      .map((t) => t.label)
      .join('; '),
  );

  protected readonly accountType = computed(
    () => ACCOUNT_TYPES.find((t) => t.value === this.app().bankAccountType)?.label ?? '',
  );

  protected readonly race = computed(
    () => RACES.find((r) => r.value === this.app().race)?.label ?? '',
  );

  protected readonly reversedHistory = computed(() => [...this.app().history].reverse());

  protected isExpired(validUntil: string | null): boolean {
    return !!validUntil && validUntil < todayIso();
  }
}
