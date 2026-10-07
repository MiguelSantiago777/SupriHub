import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Band } from './tender.api';

export type JudgmentStatus = 'AWAITING_COVER' | 'DECIDED' | 'HOMOLOGATED' | 'ANNULLED';
export type CoverStatus = 'OFFERED' | 'COVERED' | 'DECLINED' | 'EXPIRED' | 'CANCELLED';
export type SeatTerritory = 'T1' | 'T2' | 'T3' | 'OUT_OF_STATE';

export const JUDGMENT_BADGE: Record<JudgmentStatus, string> = {
  AWAITING_COVER: 'badge-warning',
  DECIDED: 'badge-info',
  HOMOLOGATED: 'badge-success',
  ANNULLED: 'badge-neutral',
};

export const COVER_BADGE: Record<CoverStatus, string> = {
  OFFERED: 'badge-warning',
  COVERED: 'badge-success',
  DECLINED: 'badge-neutral',
  EXPIRED: 'badge-neutral',
  CANCELLED: 'badge-neutral',
};

export interface JudgmentEntry {
  proposalId: number;
  applicationId: number;
  position: number | null;
  supplierName: string;
  supplierType: string | null;
  territory: SeatTerritory | null;
  totalValue: number;
  pctAbove: number | null;
  womanLed: boolean;
  blackWomanOrCadUnico: boolean;
  mei: boolean;
  fictitiousTie: boolean;
  localPreference: boolean;
  finalist: boolean;
  drawn: boolean;
  advantage: string | null;
  mustCover: boolean;
  disqualifiedReason: string | null;
  winner: boolean;
}

export interface CoverOffer {
  id: number;
  position: number;
  supplierName: string;
  status: CoverStatus;
  statusLabel: string;
  ceiling: number;
  offeredAt: string;
  dueDate: string;
  respondedAt: string | null;
  offeredValue: number | null;
}

export interface Judgment {
  id: number;
  tenderId: number;
  status: JudgmentStatus;
  statusLabel: string;
  band: Band;
  lowestValue: number;
  validCount: number;
  minValidRequired: number;
  belowMinimumJustification: string | null;
  tieMargin: number;
  localMargin: number;
  localPreferenceForAll: boolean;
  coverStrictlyLower: boolean;
  winnerProposalId: number | null;
  winnerName: string | null;
  awardedValue: number | null;
  winnerReason: string | null;
  startedAt: string;
  decidedAt: string | null;
  homologatedAt: string | null;
  homologationNote: string | null;
  annulledAt: string | null;
  annulReason: string | null;
  ataSha256: string | null;
  approvalByBoard: boolean;
  entries: JudgmentEntry[];
  covers: CoverOffer[];
  draw: { seed: number; algorithm: string; drawnAt: string; groups: string[][] } | null;
  history: {
    id: number;
    statusLabel: string;
    startedAt: string;
    annulledAt: string | null;
    annulReason: string | null;
  }[];
  version: number;
}

export interface JudgmentPanel {
  canStart: boolean;
  minValidRequired: number;
  judgment: Judgment | null;
}

export interface BoardItem {
  judgmentId: number;
  tenderId: number;
  tenderNumber: string;
  tenderTitle: string;
  lineCode: string;
  winnerName: string | null;
  awardedValue: number | null;
  estimatedValue: number;
  decidedAt: string | null;
}

export interface BoardDetail {
  tenderId: number;
  tenderNumber: string;
  tenderTitle: string;
  description: string;
  lineCode: string;
  lineName: string;
  bandLabel: string;
  estimatedValue: number;
  executionMunicipality: string | null;
  proposalDeadline: string | null;
  judgment: Judgment;
}

export interface MyJudgment {
  judging: boolean;
  cover: {
    status: CoverStatus;
    statusLabel: string;
    ceiling: number;
    dueDate: string;
    strictlyLower: boolean;
    canRespond: boolean;
    offeredValue: number | null;
  } | null;
  result: {
    winner: boolean;
    awardedValue: number;
    reason: string;
    myPosition: number | null;
    disqualified: boolean;
    disqualifiedReason: string | null;
    homologatedAt: string;
    /** Recurso contra o resultado (Anexo X): 3 dias úteis da homologação. */
    appealDeadline: string | null;
    canAppeal: boolean;
  } | null;
  appeal: {
    id: number;
    status: 'FILED' | 'FORWARDED' | 'GRANTED' | 'DENIED';
    statusLabel: string;
    filedAt: string;
    reconsiderationDue: string;
    commissionReason: string | null;
    boardDue: string | null;
    boardReason: string | null;
  } | null;
}

function openPdf(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Julgamento: Comissão, Diretoria e o próprio fornecedor. */
@Injectable({ providedIn: 'root' })
export class JudgmentApi {
  private readonly http = inject(HttpClient);

  private base(tenderId: number): string {
    return `/api/tenders/${tenderId}/judgment`;
  }

  panel(tenderId: number): Observable<JudgmentPanel> {
    return this.http.get<JudgmentPanel>(this.base(tenderId));
  }

  start(tenderId: number, justification: string | null): Observable<JudgmentPanel> {
    return this.http.post<JudgmentPanel>(`${this.base(tenderId)}/start`, { justification });
  }

  disqualify(tenderId: number, proposalId: number, reason: string): Observable<JudgmentPanel> {
    return this.http.post<JudgmentPanel>(
      `${this.base(tenderId)}/proposals/${proposalId}/disqualify`,
      { reason },
    );
  }

  requalify(tenderId: number, proposalId: number): Observable<JudgmentPanel> {
    return this.http.post<JudgmentPanel>(
      `${this.base(tenderId)}/proposals/${proposalId}/requalify`,
      {},
    );
  }

  homologate(tenderId: number, note: string | null, version: number): Observable<JudgmentPanel> {
    return this.http.post<JudgmentPanel>(`${this.base(tenderId)}/homologate`, { note, version });
  }

  annul(tenderId: number, reason: string, version: number): Observable<JudgmentPanel> {
    return this.http.post<JudgmentPanel>(`${this.base(tenderId)}/annul`, { reason, version });
  }

  openAta(judgmentId: number): void {
    this.http.get(`/api/judgments/${judgmentId}/ata`, { responseType: 'blob' }).subscribe(openPdf);
  }

  // Diretoria
  boardPending(): Observable<BoardItem[]> {
    return this.http.get<BoardItem[]>('/api/board/judgments');
  }

  boardGet(judgmentId: number): Observable<BoardDetail> {
    return this.http.get<BoardDetail>(`/api/board/judgments/${judgmentId}`);
  }

  boardApprove(judgmentId: number, note: string | null, version: number): Observable<BoardDetail> {
    return this.http.post<BoardDetail>(`/api/board/judgments/${judgmentId}/approve`, {
      note,
      version,
    });
  }

  boardReturn(judgmentId: number, reason: string, version: number): Observable<BoardDetail> {
    return this.http.post<BoardDetail>(`/api/board/judgments/${judgmentId}/return`, {
      reason,
      version,
    });
  }

  // Fornecedor
  mine(tenderId: number): Observable<MyJudgment> {
    return this.http.get<MyJudgment>(`/api/supplier/opportunities/${tenderId}/judgment`);
  }

  cover(tenderId: number, value: number): Observable<MyJudgment> {
    return this.http.post<MyJudgment>(`/api/supplier/opportunities/${tenderId}/judgment/cover`, {
      value,
    });
  }

  /** Recurso contra o resultado homologado (Anexo X). */
  appeal(
    tenderId: number,
    reasons: string,
    request: string,
    file: File | null,
  ): Observable<MyJudgment> {
    const body = new FormData();
    body.append('reasons', reasons);
    body.append('request', request);
    if (file) {
      body.append('file', file);
    }
    return this.http.post<MyJudgment>(
      `/api/supplier/opportunities/${tenderId}/judgment/appeal`,
      body,
    );
  }

  declineCover(tenderId: number): Observable<MyJudgment> {
    return this.http.post<MyJudgment>(
      `/api/supplier/opportunities/${tenderId}/judgment/cover/decline`,
      {},
    );
  }
}
