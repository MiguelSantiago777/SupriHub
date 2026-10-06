import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { SanctionDetail } from '../../core/api/control.api';
import { formatDate, formatDateTime, formatSize } from '../../core/format';

/** Linha do tempo do processo sancionador (só leitura). */
@Component({
  selector: 'app-sanction-view',
  imports: [MatButtonModule, MatIconModule],
  template: `
    @let p = process();
    <ol class="timeline">
      <li>
        <h3>Notificação — {{ dateTime(p.openedAt) }}</h3>
        <p>
          <strong>{{ p.infractionLabel }}</strong>
          @if (p.serviceOrderNumber) {
            · ordem {{ p.serviceOrderNumber }}
          }
        </p>
        <p class="pre">{{ p.facts }}</p>
        <p class="muted small">Sanção de referência (Anexo XII): {{ p.infractionReference }}</p>
        @if (p.interimBlock) {
          <p class="alert alert-warning">
            Cadastro inativado cautelarmente até a decisão (art. 26).
          </p>
        }
      </li>
      <li>
        <h3>Defesa — prazo {{ date(p.defenseDue) }}</h3>
        @if (p.defenseText) {
          <p class="muted small">Apresentada em {{ dateTime(p.defenseAt) }}</p>
          <p class="pre">{{ p.defenseText }}</p>
        } @else if (p.status === 'DEFENSE') {
          <p class="muted">Aguardando a defesa.</p>
        } @else {
          <p class="muted">Sem defesa no prazo.</p>
        }
      </li>
      @if (p.decidedAt) {
        <li>
          <h3>Decisão da Comissão — {{ dateTime(p.decidedAt) }}</h3>
          <p>
            <strong>{{ p.sanctionLabel }}</strong
            >{{ dosimetry(p.sanction, p.finePct, p.suspensionMonths, p.impedimentYears) }}
          </p>
          <p class="pre">{{ p.decisionReason }}</p>
          @if (p.appealDue && p.status === 'APPEAL_WINDOW') {
            <p class="muted small">Recurso à Diretoria até {{ date(p.appealDue) }}.</p>
          }
        </li>
      } @else if (p.decisionDue) {
        <li>
          <h3>Decisão da Comissão — prazo {{ date(p.decisionDue) }}</h3>
          <p class="muted">Aguardando a decisão motivada.</p>
        </li>
      }
      @if (p.appealedAt) {
        <li>
          <h3>Recurso à Diretoria — {{ dateTime(p.appealedAt) }}</h3>
          <p class="pre">{{ p.appealText }}</p>
          @if (p.boardAt) {
            <p>
              <strong>{{ p.boardOutcomeLabel }}</strong> em {{ dateTime(p.boardAt) }}
            </p>
            <p class="pre">{{ p.boardReason }}</p>
          } @else {
            <p class="muted small">Decisão final até {{ date(p.boardDue) }}.</p>
          }
        </li>
      }
      @if (p.finalAt) {
        <li class="final">
          <h3>Encerrado — {{ dateTime(p.finalAt) }}</h3>
          <p>
            <strong>{{ p.finalSanctionLabel }}</strong
            >{{ dosimetry(p.finalSanction, p.finalFinePct, p.finalMonths, p.finalYears) }}
            @if (p.effectiveUntil) {
              — fora das oportunidades até <strong>{{ date(p.effectiveUntil) }}</strong>
            }
          </p>
        </li>
      }
    </ol>

    @if (p.files.length) {
      <h3 class="files-title">Arquivos</h3>
      <ul class="files">
        @for (f of p.files; track f.id) {
          <li>
            <button mat-button type="button" (click)="openFile.emit(f.id)">
              <mat-icon aria-hidden="true">attach_file</mat-icon> {{ f.name }}
            </button>
            <span class="muted small">{{ f.kindLabel }} · {{ size(f.size) }}</span>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .timeline {
      list-style: none;
      margin: 0;
      padding: 0 0 0 1.25rem;
      border-left: 3px solid var(--sh-border);
      display: grid;
      gap: 1.25rem;
      li {
        position: relative;
      }
      li::before {
        content: '';
        position: absolute;
        left: calc(-1.25rem - 8px);
        top: 0.35rem;
        width: 13px;
        height: 13px;
        border-radius: 50%;
        background: var(--sh-primary);
      }
      li.final::before {
        background: var(--sh-navy);
      }
      h3 {
        font-size: 1rem;
        margin: 0 0 0.35rem;
      }
      p {
        margin: 0 0 0.35rem;
      }
    }
    .pre {
      white-space: pre-line;
    }
    .small {
      font-size: 0.9rem;
    }
    .files-title {
      font-size: 1rem;
      margin: 1.25rem 0 0.25rem;
    }
    .files {
      list-style: none;
      padding: 0;
      margin: 0;
      li {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.25rem 0.5rem;
      }
    }
  `,
})
export class SanctionView {
  readonly process = input.required<SanctionDetail>();
  readonly openFile = output<number>();

  protected readonly date = formatDate;
  protected readonly dateTime = formatDateTime;
  protected readonly size = formatSize;

  protected dosimetry(
    s: string | null,
    pct: number | null,
    months: number | null,
    years: number | null,
  ): string {
    if (s === 'FINE' && pct) {
      return ` de ${pct}%`;
    }
    if (s === 'SUSPENSION' && months) {
      return ` por ${months} ${months === 1 ? 'mês' : 'meses'}`;
    }
    if (s === 'DEREGISTRATION' && years) {
      return `, impedimento por ${years} ${years === 1 ? 'ano' : 'anos'}`;
    }
    return '';
  }
}
