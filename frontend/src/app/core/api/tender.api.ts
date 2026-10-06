import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../models';
import { Territory } from './supplier.api';

export type TenderStatus = 'DRAFT' | 'OPEN' | 'JUDGING' | 'AWARDED' | 'CANCELLED';
export type Band = 'FAIXA_1' | 'FAIXA_2' | 'FAIXA_3';
export type Audience = 'ALL' | 'WOMEN';
export type InvitationStatus =
  'INVITED' | 'ACCEPTED' | 'CONFIRMED' | 'DECLINED' | 'EXPIRED' | 'PRICE_REJECTED';
export type ProposalMode = 'ONLINE' | 'MODEL' | 'OWN_FILE';

export const TENDER_BADGE: Record<TenderStatus, string> = {
  DRAFT: 'badge-neutral',
  OPEN: 'badge-info',
  JUDGING: 'badge-warning',
  AWARDED: 'badge-success',
  CANCELLED: 'badge-error',
};

export const INVITATION_BADGE: Record<InvitationStatus, string> = {
  INVITED: 'badge-warning',
  ACCEPTED: 'badge-info',
  CONFIRMED: 'badge-success',
  DECLINED: 'badge-neutral',
  EXPIRED: 'badge-neutral',
  PRICE_REJECTED: 'badge-error',
};

export interface TenderItem {
  id: number;
  position: number;
  description: string;
  unit: string;
  quantity: number;
}

export interface TenderItemInput {
  description: string;
  unit: string;
  quantity: number | null;
}

export interface TenderInput {
  title: string;
  description: string;
  lineCode: string;
  executionMunicipalityId: number | null;
  location: string | null;
  schedule: string | null;
  executionDeadline: string | null;
  requirements: string | null;
  paymentTerms: string | null;
  audience: Audience;
  estimatedValue: number | null;
  proposalDeadline: string | null;
  territories: Territory[];
  items: TenderItemInput[];
}

export interface TenderSummary {
  id: number;
  number: string | null;
  status: TenderStatus;
  statusLabel: string;
  title: string;
  lineCode: string;
  lineName: string;
  band: Band;
  bandLabel: string;
  estimatedValue: number;
  proposalDeadline: string | null;
  deadlinePassed: boolean;
  proposalCount: number;
  pendingSupplier: string | null;
  pendingDueDate: string | null;
  publishedAt: string | null;
  createdAt: string;
}

export interface Invitation {
  id: number;
  applicationId: number;
  supplierName: string | null;
  status: InvitationStatus;
  statusLabel: string;
  invitedAt: string;
  dueDate: string;
  respondedAt: string | null;
  declineReason: string | null;
  proposedTotal: number | null;
}

export interface ProposalRow {
  id: number;
  applicationId: number;
  supplierName: string | null;
  supplierTypeLabel: string | null;
  womanLed: boolean;
  seatTerritory: Territory | 'OUT_OF_STATE' | null;
  mode: ProposalMode;
  modeLabel: string;
  totalValue: number;
  unitPrices: Record<string, number>;
  executionDeadline: string | null;
  validityDays: number;
  specialConditions: string | null;
  notes: string | null;
  fileName: string | null;
  submittedAt: string;
  disqualifiedReason: string | null;
}

export interface TenderDetail {
  id: number;
  number: string | null;
  status: TenderStatus;
  statusLabel: string;
  title: string;
  description: string;
  line: { code: string; shortName: string };
  executionMunicipalityId: number | null;
  executionMunicipalityName: string | null;
  location: string | null;
  schedule: string | null;
  executionDeadline: string | null;
  requirements: string | null;
  paymentTerms: string | null;
  audience: Audience;
  audienceLabel: string;
  estimatedValue: number;
  band: Band;
  bandLabel: string;
  selectionMode: string;
  proposalDeadline: string | null;
  deadlinePassed: boolean;
  publishedAt: string | null;
  cancelReason: string | null;
  territories: Territory[];
  items: TenderItem[];
  invitations: Invitation[];
  proposalsSealed: boolean;
  proposalCount: number;
  proposals: ProposalRow[];
  eligibleCount: number;
  /** Banco de Preços: teto (média + margem) por item, para conferir o preço do rodízio. */
  priceReferences: { itemId: number; count: number; average: number; ceiling: number }[];
  ceilingTotal: number | null;
  version: number;
}

export interface MyProposal {
  id: number;
  mode: ProposalMode;
  modeLabel: string;
  totalValue: number;
  unitPrices: Record<string, number>;
  executionDeadline: string | null;
  validityDays: number;
  specialConditions: string | null;
  notes: string | null;
  fileName: string | null;
  submittedAt: string;
  updatedAt: string;
}

export interface Opportunity {
  id: number;
  number: string | null;
  status: TenderStatus;
  title: string;
  description: string;
  line: { code: string; shortName: string };
  executionMunicipalityName: string | null;
  location: string | null;
  schedule: string | null;
  executionDeadline: string | null;
  requirements: string | null;
  paymentTerms: string | null;
  audienceLabel: string;
  band: Band;
  bandLabel: string;
  selectionMode: string;
  proposalDeadline: string | null;
  deadlinePassed: boolean;
  territories: Territory[];
  items: TenderItem[];
  invitation: { status: InvitationStatus; statusLabel: string; dueDate: string } | null;
  proposal: MyProposal | null;
  canSubmit: boolean;
  canWithdraw: boolean;
  minValidityDays: number;
}

export interface ProposalInput {
  mode: ProposalMode;
  unitPrices: Record<number, number>;
  totalValue: number | null;
  executionDeadline: string | null;
  validityDays: number;
  specialConditions: string | null;
  notes: string | null;
  declarationAccepted: boolean;
}

function download(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function open(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Comissão / Admin. */
@Injectable({ providedIn: 'root' })
export class TenderApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/tenders';

  list(statuses: TenderStatus[], page = 0): Observable<Page<TenderSummary>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    statuses.forEach((s) => (params = params.append('status', s)));
    return this.http.get<Page<TenderSummary>>(this.base, { params });
  }

  counts(): Observable<Record<TenderStatus, number>> {
    return this.http.get<Record<TenderStatus, number>>(`${this.base}/counts`);
  }

  get(id: number): Observable<TenderDetail> {
    return this.http.get<TenderDetail>(`${this.base}/${id}`);
  }

  create(input: TenderInput): Observable<TenderDetail> {
    return this.http.post<TenderDetail>(this.base, { ...input, version: 0 });
  }

  update(id: number, input: TenderInput, version: number): Observable<TenderDetail> {
    return this.http.put<TenderDetail>(`${this.base}/${id}`, { ...input, version });
  }

  publish(id: number, version: number): Observable<TenderDetail> {
    return this.http.post<TenderDetail>(`${this.base}/${id}/publish`, { version });
  }

  cancel(id: number, reason: string, version: number): Observable<TenderDetail> {
    return this.http.post<TenderDetail>(`${this.base}/${id}/cancel`, { reason, version });
  }

  expand(id: number, territories: Territory[], version: number): Observable<TenderDetail> {
    return this.http.post<TenderDetail>(`${this.base}/${id}/expand-territories`, {
      territories,
      version,
    });
  }

  confirm(invitationId: number): Observable<TenderDetail> {
    return this.http.post<TenderDetail>(`${this.base}/invitations/${invitationId}/confirm`, {});
  }

  rejectPrice(invitationId: number, reason: string): Observable<TenderDetail> {
    return this.http.post<TenderDetail>(`${this.base}/invitations/${invitationId}/reject-price`, {
      reason,
      version: 0,
    });
  }

  openProposalFile(id: number, proposalId: number): void {
    this.http
      .get(`${this.base}/${id}/proposals/${proposalId}/file`, { responseType: 'blob' })
      .subscribe(open);
  }

  band(estimatedValue: number): Observable<{ band: Band; label: string; selectionMode: string }> {
    return this.http.get<{ band: Band; label: string; selectionMode: string }>(
      '/api/public/bands',
      {
        params: new HttpParams().set('estimatedValue', estimatedValue),
      },
    );
  }
}

/** Fornecedor. */
@Injectable({ providedIn: 'root' })
export class OpportunityApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/supplier/opportunities';

  list(): Observable<Opportunity[]> {
    return this.http.get<Opportunity[]>(this.base);
  }

  get(id: number): Observable<Opportunity> {
    return this.http.get<Opportunity>(`${this.base}/${id}`);
  }

  downloadModel(id: number, number: string | null): void {
    this.http
      .get(`${this.base}/${id}/proposal-model`, { responseType: 'blob' })
      .subscribe((b) => download(b, `Proposta-${number ?? id}.docx`));
  }

  submit(id: number, input: ProposalInput, file: File | null): Observable<Opportunity> {
    const body = new FormData();
    body.append('data', new Blob([JSON.stringify(input)], { type: 'application/json' }));
    if (file) {
      body.append('file', file);
    }
    return this.http.post<Opportunity>(`${this.base}/${id}/proposal`, body);
  }

  withdraw(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/proposal`);
  }

  decline(id: number, reason: string): Observable<Opportunity> {
    return this.http.post<Opportunity>(`${this.base}/${id}/decline`, { reason });
  }

  openMyFile(id: number): void {
    this.http.get(`${this.base}/${id}/proposal/file`, { responseType: 'blob' }).subscribe(open);
  }
}
