import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../models';
import { Band } from './tender.api';

// ------------------------------------------------------------------- sanções

export type SanctionStatus =
  'DEFENSE' | 'AWAITING_DECISION' | 'APPEAL_WINDOW' | 'BOARD_REVIEW' | 'FINAL';
export type SanctionKind = 'NONE' | 'WARNING' | 'FINE' | 'SUSPENSION' | 'DEREGISTRATION';
export type BoardOutcome = 'UPHELD' | 'REFORMED' | 'ANNULLED';

export const SANCTION_BADGE: Record<SanctionStatus, string> = {
  DEFENSE: 'badge-warning',
  AWAITING_DECISION: 'badge-info',
  APPEAL_WINDOW: 'badge-warning',
  BOARD_REVIEW: 'badge-info',
  FINAL: 'badge-neutral',
};

export const SANCTION_LABELS: Record<SanctionKind, string> = {
  NONE: 'Arquivamento — sem sanção',
  WARNING: 'Advertência escrita',
  FINE: 'Multa (prevista na ordem)',
  SUSPENSION: 'Suspensão do cadastro (1 a 24 meses)',
  DEREGISTRATION: 'Descredenciamento com impedimento (1 a 3 anos)',
};

export interface Infraction {
  code: string;
  label: string;
  reference: string;
}

export interface SanctionSummary {
  id: number;
  number: string;
  status: SanctionStatus;
  statusLabel: string;
  infractionLabel: string;
  applicationId: number;
  supplierName: string | null;
  openedAt: string;
  nextDue: string | null;
  finalSanctionLabel: string | null;
  effectiveUntil: string | null;
}

export interface SanctionFile {
  id: number;
  kind: string;
  kindLabel: string;
  name: string;
  size: number;
  uploadedAt: string;
}

export interface SanctionDetail {
  id: number;
  number: string;
  status: SanctionStatus;
  statusLabel: string;
  infraction: string;
  infractionLabel: string;
  infractionReference: string;
  facts: string;
  interimBlock: boolean;
  applicationId: number;
  supplierName: string | null;
  serviceOrderId: number | null;
  serviceOrderNumber: string | null;
  orderPenaltyPct: number | null;
  openedAt: string;
  defenseDue: string;
  defenseText: string | null;
  defenseAt: string | null;
  decisionDue: string | null;
  sanction: SanctionKind | null;
  sanctionLabel: string | null;
  finePct: number | null;
  suspensionMonths: number | null;
  impedimentYears: number | null;
  decisionReason: string | null;
  decidedAt: string | null;
  appealDue: string | null;
  appealText: string | null;
  appealedAt: string | null;
  boardDue: string | null;
  boardOutcome: BoardOutcome | null;
  boardOutcomeLabel: string | null;
  boardReason: string | null;
  boardAt: string | null;
  finalSanction: SanctionKind | null;
  finalSanctionLabel: string | null;
  finalFinePct: number | null;
  finalMonths: number | null;
  finalYears: number | null;
  effectiveUntil: string | null;
  finalAt: string | null;
  files: SanctionFile[];
  previous: SanctionSummary[];
  recidivist: boolean;
  canDefend: boolean;
  canAppeal: boolean;
  version: number;
}

export interface SanctionDecision {
  sanction: SanctionKind;
  finePct: number | null;
  months: number | null;
  years: number | null;
  reason: string;
}

function open(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function textBody(text: string, file: File | null): FormData {
  const body = new FormData();
  body.append('data', new Blob([JSON.stringify({ text })], { type: 'application/json' }));
  if (file) {
    body.append('file', file);
  }
  return body;
}

@Injectable({ providedIn: 'root' })
export class SanctionApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/sanctions';

  infractions(): Observable<Infraction[]> {
    return this.http.get<Infraction[]>(`${this.base}/infractions`);
  }

  list(statuses: SanctionStatus[], page = 0): Observable<Page<SanctionSummary>> {
    let params = new HttpParams().set('page', page);
    statuses.forEach((s) => (params = params.append('status', s)));
    return this.http.get<Page<SanctionSummary>>(this.base, { params });
  }

  counts(): Observable<Record<SanctionStatus, number>> {
    return this.http.get<Record<SanctionStatus, number>>(`${this.base}/counts`);
  }

  get(id: number): Observable<SanctionDetail> {
    return this.http.get<SanctionDetail>(`${this.base}/${id}`);
  }

  open(input: {
    applicationId: number;
    serviceOrderId: number | null;
    infraction: string;
    facts: string;
    interimBlock: boolean;
  }): Observable<SanctionDetail> {
    return this.http.post<SanctionDetail>(this.base, input);
  }

  evidence(id: number, file: File): Observable<SanctionDetail> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<SanctionDetail>(`${this.base}/${id}/evidence`, body);
  }

  decide(id: number, d: SanctionDecision, version: number): Observable<SanctionDetail> {
    return this.http.post<SanctionDetail>(`${this.base}/${id}/decision`, { ...d, version });
  }

  board(
    id: number,
    outcome: BoardOutcome,
    d: Partial<SanctionDecision>,
    reason: string,
    version: number,
  ): Observable<SanctionDetail> {
    return this.http.post<SanctionDetail>(`${this.base}/${id}/board-decision`, {
      outcome,
      sanction: outcome === 'REFORMED' ? d.sanction : null,
      finePct: d.finePct ?? null,
      months: d.months ?? null,
      years: d.years ?? null,
      reason,
      version,
    });
  }

  openFile(id: number, fileId: number): void {
    this.http.get(`${this.base}/${id}/files/${fileId}`, { responseType: 'blob' }).subscribe(open);
  }
}

@Injectable({ providedIn: 'root' })
export class SupplierSanctionApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/supplier/sanctions';

  list(): Observable<SanctionSummary[]> {
    return this.http.get<SanctionSummary[]>(this.base);
  }

  get(id: number): Observable<SanctionDetail> {
    return this.http.get<SanctionDetail>(`${this.base}/${id}`);
  }

  defend(id: number, text: string, file: File | null): Observable<SanctionDetail> {
    return this.http.post<SanctionDetail>(`${this.base}/${id}/defense`, textBody(text, file));
  }

  appeal(id: number, text: string, file: File | null): Observable<SanctionDetail> {
    return this.http.post<SanctionDetail>(`${this.base}/${id}/appeal`, textBody(text, file));
  }

  openFile(id: number, fileId: number): void {
    this.http.get(`${this.base}/${id}/files/${fileId}`, { responseType: 'blob' }).subscribe(open);
  }
}

// ----------------------------------------------------------- Banco de Preços

export type PriceSource = 'CONTRACT' | 'QUOTATION' | 'PRICE_PANEL' | 'RESEARCH';

export interface PriceItem {
  lineCode: string;
  lineName: string;
  description: string;
  unit: string;
  count: number;
  average: number;
  min: number;
  max: number;
  ceiling: number;
  lastObserved: string;
  entries: {
    id: number;
    unitPrice: number;
    source: PriceSource;
    sourceLabel: string;
    reference: string | null;
    observedOn: string;
  }[];
}

@Injectable({ providedIn: 'root' })
export class PriceBankApi {
  private readonly http = inject(HttpClient);

  search(line: string | null, q: string): Observable<PriceItem[]> {
    let params = new HttpParams().set('q', q);
    if (line) {
      params = params.set('line', line);
    }
    return this.http.get<PriceItem[]>('/api/price-bank', { params });
  }

  add(input: {
    lineCode: string;
    description: string;
    unit: string;
    unitPrice: number | null;
    source: PriceSource;
    reference: string | null;
    observedOn: string;
  }): Observable<unknown> {
    return this.http.post('/api/price-bank', input);
  }
}

// -------------------------------------------------------------------- dossiê

export type DossierState = 'OK' | 'PENDING' | 'NOT_APPLICABLE';

export interface Dossier {
  orderId: number;
  orderNumber: string | null;
  tenderNumber: string;
  bandLabel: string;
  items: {
    no: number;
    label: string;
    manual: boolean;
    optional: boolean;
    state: DossierState;
    detail: string | null;
  }[];
  complete: boolean;
  pending: number;
  reviews: { id: number; complete: boolean; note: string | null; reviewedAt: string }[];
}

export interface DossierSample {
  from: string;
  to: string;
  paid: number;
  reviewed: number;
  minimum: number;
  rows: {
    orderId: number;
    orderNumber: string;
    value: number;
    paidOn: string;
    complete: boolean | null;
    reviewedAt: string | null;
  }[];
}

@Injectable({ providedIn: 'root' })
export class DossierApi {
  private readonly http = inject(HttpClient);

  get(orderId: number): Observable<Dossier> {
    return this.http.get<Dossier>(`/api/dossiers/${orderId}`);
  }

  check(
    orderId: number,
    itemNo: number,
    checked: boolean,
    note: string | null,
  ): Observable<Dossier> {
    return this.http.post<Dossier>(`/api/dossiers/${orderId}/items/${itemNo}`, { checked, note });
  }

  review(orderId: number, note: string | null): Observable<Dossier> {
    return this.http.post<Dossier>(`/api/dossiers/${orderId}/review`, { note });
  }

  sample(from?: string, to?: string): Observable<DossierSample> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<DossierSample>('/api/dossiers/sample', { params });
  }
}

// --------------------------------------------------------------- indicadores

export interface IndicatorReport {
  from: string;
  to: string;
  contracts: number;
  totalValue: number;
  activeSuppliers: number;
  indicators: {
    code: string;
    label: string;
    formula: string;
    value: number | null;
    goal: number;
    goalLabel: string;
    met: boolean | null;
    note: string | null;
  }[];
  bands: {
    band: Band;
    label: string;
    count: number;
    value: number;
    avgBusinessDays: number | null;
  }[];
  projects: { project: string; count: number; value: number }[];
  lines: { code: string; name: string; activeT1: number; active: number }[];
}

@Injectable({ providedIn: 'root' })
export class IndicatorApi {
  private readonly http = inject(HttpClient);

  report(from?: string, to?: string): Observable<IndicatorReport> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<IndicatorReport>('/api/indicators', { params });
  }
}
