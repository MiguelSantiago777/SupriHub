import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { COVER_BADGE, Judgment, JudgmentEntry } from '../../core/api/judgment.api';
import { formatDate, formatDateTime, formatMoney } from '../../core/format';

const TERRITORY: Record<string, string> = {
  T1: 'T1',
  T2: 'T2',
  T3: 'T3',
  OUT_OF_STATE: 'Fora do RJ',
};

/** Mapa comparativo, convocações para cobrir, sorteio e resultado (só leitura). */
@Component({
  selector: 'app-judgment-map',
  imports: [MatIconModule],
  template: `
    @let j = judgment();
    <div class="rules muted small">
      Empate ficto: pequenos negócios até {{ pct(j.tieMargin) }} acima da menor proposta ({{
        money(j.lowestValue)
      }}). Depois: território da sede, mulher, mulher negra ou CadÚnico, MEI, avaliação, menor %
      acima e sorteio. Para cobrir:
      {{ j.coverStrictlyLower ? 'valor menor que a menor proposta' : 'valor igual ou menor' }}.
    </div>

    <div class="table-wrap">
      <table class="data jmap">
        <caption class="visually-hidden">
          Mapa comparativo do julgamento
        </caption>
        <thead>
          <tr>
            <th scope="col">Pos.</th>
            <th scope="col">Fornecedor</th>
            <th scope="col">Terr.</th>
            <th scope="col" class="num">Valor</th>
            <th scope="col" class="num">% acima</th>
            <th scope="col">Situação</th>
          </tr>
        </thead>
        <tbody>
          @for (e of ranked(); track e.proposalId) {
            <tr [class.win]="e.winner" [class.out]="!e.finalist">
              <td>{{ e.position }}º</td>
              <td>
                <strong>{{ e.supplierName }}</strong>
                <span class="tags">
                  <span class="muted">{{ e.supplierType }}</span>
                  @if (e.womanLed) {
                    <span class="badge badge-info">mulher</span>
                  }
                  @if (showEquity() && e.blackWomanOrCadUnico) {
                    <span class="badge badge-info" title="Dado sigiloso (Política art. 28)"
                      >negra/CadÚnico</span
                    >
                  }
                  @if (e.mei) {
                    <span class="badge badge-neutral">MEI</span>
                  }
                </span>
              </td>
              <td>{{ territory(e.territory) }}</td>
              <td class="num">
                <strong>{{ money(e.totalValue) }}</strong>
              </td>
              <td class="num">{{ e.pctAbove === null ? '—' : pct(e.pctAbove) }}</td>
              <td>
                @if (e.winner) {
                  <span class="badge badge-success">Vencedora</span>
                } @else if (!e.finalist) {
                  <span class="muted">Fora da margem</span>
                } @else if (e.pctAbove === 0) {
                  <span class="badge badge-neutral">Menor preço</span>
                } @else {
                  <span class="badge badge-warning">{{
                    e.localPreference ? 'Preferência local' : 'Empate ficto'
                  }}</span>
                }
                @if (e.drawn) {
                  <span class="badge badge-neutral">sorteio</span>
                }
                @if (e.finalist && e.advantage && e.advantage !== '—' && e.pctAbove !== 0) {
                  <small class="adv">Prioridade: {{ e.advantage }}</small>
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>

    @if (disqualified().length) {
      <h3>Desclassificadas</h3>
      <ul class="plain">
        @for (e of disqualified(); track e.proposalId) {
          <li>
            <strong>{{ e.supplierName }}</strong> — {{ money(e.totalValue) }}
            <span class="muted">· {{ e.disqualifiedReason }}</span>
          </li>
        }
      </ul>
    }

    @if (j.draw; as d) {
      <h3><mat-icon aria-hidden="true">casino</mat-icon> Sorteio</h3>
      <p class="small">
        Empate exato em todos os critérios. Sorteado pelo sistema em
        {{ dateTime(d.drawnAt) }} (semente {{ d.seed }}).
        @for (g of d.groups; track $index) {
          <br />Ordem: {{ g.join(', ') }}
        }
      </p>
    }

    @if (j.covers.length) {
      <h3><mat-icon aria-hidden="true">price_change</mat-icon> Direito de cobrir</h3>
      <ol class="covers">
        @for (c of j.covers; track c.id) {
          <li>
            <span
              ><strong>{{ c.position }}º · {{ c.supplierName }}</strong></span
            >
            <span class="badge" [class]="coverBadge[c.status]">{{ c.statusLabel }}</span>
            <span class="muted small">
              convocada em {{ dateTime(c.offeredAt) }} · prazo {{ date(c.dueDate) }}
              @if (c.offeredValue !== null) {
                · ofereceu <strong>{{ money(c.offeredValue) }}</strong>
              }
            </span>
          </li>
        }
      </ol>
    }

    @if (j.winnerName) {
      <div class="result">
        <mat-icon aria-hidden="true">emoji_events</mat-icon>
        <div>
          <strong>{{ j.winnerName }}</strong> — {{ money(j.awardedValue) }}
          <p>{{ j.winnerReason }}</p>
        </div>
      </div>
    }
    @if (j.belowMinimumJustification) {
      <p class="small">
        <strong>Julgado com {{ j.validCount }} proposta(s) válida(s)</strong> (mínimo
        {{ j.minValidRequired }}): {{ j.belowMinimumJustification }}
      </p>
    }
  `,
  styles: `
    .rules {
      margin: 0 0 0.75rem;
    }
    .small {
      font-size: 0.9rem;
    }
    .num {
      text-align: right;
      white-space: nowrap;
    }
    .jmap {
      tr.win td {
        background: var(--sh-green-soft);
      }
      tr.out td {
        color: var(--sh-text-muted);
      }
      .tags {
        display: flex;
        flex-wrap: wrap;
        gap: 0.3rem;
        align-items: center;
        margin-top: 0.15rem;
      }
      .badge + .badge {
        margin-left: 0.3rem;
      }
      .adv {
        display: block;
        margin-top: 0.25rem;
        color: var(--sh-text-muted);
      }
    }
    h3 {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 1.02rem;
      margin: 1.25rem 0 0.5rem;
      mat-icon {
        color: var(--sh-primary);
      }
    }
    .plain {
      margin: 0;
      padding-left: 1.2rem;
    }
    .covers {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.5rem;
      li {
        display: flex;
        flex-wrap: wrap;
        gap: 0.4rem 0.75rem;
        align-items: center;
        padding: 0.6rem 0.85rem;
        border: 1px solid var(--sh-border);
        border-radius: var(--sh-radius-sm);
      }
    }
    .result {
      display: flex;
      gap: 0.75rem;
      align-items: flex-start;
      margin-top: 1.25rem;
      padding: 0.9rem 1rem;
      border-radius: var(--sh-radius-sm);
      background: var(--sh-green-soft);
      mat-icon {
        color: var(--sh-green);
      }
      p {
        margin: 0.25rem 0 0;
      }
    }
  `,
})
export class JudgmentMap {
  readonly judgment = input.required<Judgment>();
  /** Mostra o indicador combinado de raça/CadÚnico (só equipe interna). */
  readonly showEquity = input(true);

  protected readonly coverBadge = COVER_BADGE;
  protected readonly money = formatMoney;
  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;

  protected readonly ranked = computed(() =>
    this.judgment().entries.filter((e: JudgmentEntry) => e.position !== null),
  );
  protected readonly disqualified = computed(() =>
    this.judgment().entries.filter((e: JudgmentEntry) => e.position === null),
  );

  protected pct(v: number): string {
    return (v * 100).toLocaleString('pt-BR', { maximumFractionDigits: 2 }) + '%';
  }

  protected territory(t: string | null): string {
    return t ? (TERRITORY[t] ?? t) : '—';
  }
}
