import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../models';
import { Band } from './tender.api';

export type OrderStatus =
  | 'DRAFT'
  | 'ISSUED'
  | 'EXPIRED'
  | 'SIGNED'
  | 'RECEIVED_WITH_RESERVATIONS'
  | 'RECEIVED'
  | 'REJECTED'
  | 'INVOICED'
  | 'PAID'
  | 'CANCELLED';
export type OrderKind = 'SUPPLY' | 'SERVICE';
export type ReceiptKind = 'DEFINITIVE' | 'WITH_RESERVATIONS' | 'REJECTED';
export type FileKind = 'EVIDENCE' | 'INVOICE' | 'PAYMENT_PROOF';
export type PaymentMethod = 'PIX' | 'TRANSFER';

export const ORDER_BADGE: Record<OrderStatus, string> = {
  DRAFT: 'badge-neutral',
  ISSUED: 'badge-warning',
  EXPIRED: 'badge-error',
  SIGNED: 'badge-info',
  RECEIVED_WITH_RESERVATIONS: 'badge-warning',
  RECEIVED: 'badge-info',
  REJECTED: 'badge-error',
  INVOICED: 'badge-warning',
  PAID: 'badge-success',
  CANCELLED: 'badge-neutral',
};

export interface OrderSummary {
  id: number;
  number: string | null;
  status: OrderStatus;
  statusLabel: string;
  kindLabel: string;
  tenderId: number;
  tenderNumber: string;
  tenderTitle: string;
  supplierName: string | null;
  totalValue: number;
  signatureDueDate: string | null;
  paymentDueDate: string | null;
  rating: number | null;
  updatedAt: string;
}

export interface Receipt {
  id: number;
  kind: ReceiptKind;
  kindLabel: string;
  executionDate: string;
  reservations: string | null;
  correctionDays: number | null;
  rejectReason: string | null;
  scoreDeadline: number;
  scoreQuality: number;
  scoreDocumentation: number;
  scoreService: number;
  scoreSafety: number;
  average: number;
  notes: string | null;
  evidenceKinds: string | null;
  receivedAt: string;
  supplierAckAt: string | null;
}

export interface OrderFile {
  id: number;
  kind: FileKind;
  kindLabel: string;
  name: string;
  size: number;
  uploadedAt: string;
}

export interface OrderDetail {
  id: number;
  number: string | null;
  status: OrderStatus;
  statusLabel: string;
  kind: OrderKind;
  kindLabel: string;
  tender: {
    id: number;
    number: string;
    title: string;
    band: Band;
    bandLabel: string;
    lineCode: string;
    lineName: string;
  };
  supplier: {
    applicationId: number;
    name: string;
    typeLabel: string | null;
    regular: boolean;
  } | null;
  totalValue: number;
  project: string | null;
  objectDescription: string;
  schedule: string | null;
  location: string | null;
  paymentDays: number;
  upfrontValue: number | null;
  penaltyPct: number | null;
  notes: string | null;
  acceptanceBased: boolean;
  items: {
    position: number;
    description: string;
    unit: string;
    quantity: number;
    unitPrice: number | null;
  }[];
  issuedAt: string | null;
  signatureDueDate: string | null;
  signedAt: string | null;
  signedVia: string | null;
  receivedAt: string | null;
  paymentDueDate: string | null;
  rating: number | null;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  invoiceValue: number | null;
  invoicedAt: string | null;
  paidOn: string | null;
  paidValue: number | null;
  paymentMethod: PaymentMethod | null;
  paymentMethodLabel: string | null;
  cancelReason: string | null;
  receipts: Receipt[];
  files: OrderFile[];
  canSign: boolean;
  version: number;
}

export interface OrderTerms {
  kind: OrderKind;
  project: string | null;
  objectDescription: string;
  schedule: string | null;
  location: string | null;
  paymentDays: number;
  upfrontValue: number | null;
  penaltyPct: number | null;
  notes: string | null;
}

export interface ReceiptInput {
  kind: ReceiptKind;
  executionDate: string;
  reservations: string | null;
  correctionDays: number | null;
  rejectReason: string | null;
  scoreDeadline: number;
  scoreQuality: number;
  scoreDocumentation: number;
  scoreService: number;
  scoreSafety: number;
  notes: string | null;
  evidenceKinds: string | null;
}

export interface InvoiceInput {
  number: string;
  date: string;
  value: number | null;
}

export const SCORE_CRITERIA: { key: keyof ReceiptInput; label: string }[] = [
  { key: 'scoreDeadline', label: 'Cumprimento do prazo e horários' },
  { key: 'scoreQuality', label: 'Qualidade e conformidade com a especificação' },
  { key: 'scoreDocumentation', label: 'Documentação (nota fiscal/recibo correto e no prazo)' },
  { key: 'scoreService', label: 'Atendimento, comunicação e postura com os participantes' },
  { key: 'scoreSafety', label: 'Segurança, acessibilidade e sustentabilidade' },
];

function open(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function invoiceBody(input: InvoiceInput, file: File | null): FormData {
  const body = new FormData();
  body.append('data', new Blob([JSON.stringify(input)], { type: 'application/json' }));
  if (file) {
    body.append('file', file);
  }
  return body;
}

/** Equipe interna. */
@Injectable({ providedIn: 'root' })
export class ContractApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/contracts';

  list(statuses: OrderStatus[], page = 0): Observable<Page<OrderSummary>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    statuses.forEach((s) => (params = params.append('status', s)));
    return this.http.get<Page<OrderSummary>>(this.base, { params });
  }

  counts(): Observable<Record<OrderStatus, number>> {
    return this.http.get<Record<OrderStatus, number>>(`${this.base}/counts`);
  }

  byTender(tenderId: number): Observable<OrderSummary> {
    return this.http.get<OrderSummary>(`${this.base}/by-tender/${tenderId}`);
  }

  get(id: number): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.base}/${id}`);
  }

  update(id: number, terms: OrderTerms, version: number): Observable<OrderDetail> {
    return this.http.put<OrderDetail>(`${this.base}/${id}`, { ...terms, version });
  }

  issue(id: number, version: number): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/${id}/issue`, { version });
  }

  cancel(id: number, reason: string, version: number): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/${id}/cancel`, { reason, version });
  }

  receive(id: number, input: ReceiptInput): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/${id}/receipts`, input);
  }

  upload(id: number, kind: FileKind, file: File): Observable<OrderDetail> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<OrderDetail>(`${this.base}/${id}/files`, body, {
      params: new HttpParams().set('kind', kind),
    });
  }

  invoice(id: number, input: InvoiceInput, file: File | null): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/${id}/invoice`, invoiceBody(input, file));
  }

  pay(
    id: number,
    paidOn: string,
    value: number,
    method: PaymentMethod,
    version: number,
  ): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/${id}/payment`, {
      paidOn,
      value,
      method,
      version,
    });
  }

  openFile(id: number, fileId: number): void {
    this.http.get(`${this.base}/${id}/files/${fileId}`, { responseType: 'blob' }).subscribe(open);
  }

  openPdf(id: number): void {
    this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob' }).subscribe(open);
  }
}

/** Fornecedor. */
@Injectable({ providedIn: 'root' })
export class SupplierContractApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/supplier/contracts';

  list(): Observable<OrderSummary[]> {
    return this.http.get<OrderSummary[]>(this.base);
  }

  get(id: number): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${this.base}/${id}`);
  }

  sign(id: number): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/${id}/sign`, { declarationAccepted: true });
  }

  acknowledge(id: number): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/${id}/acknowledge`, {});
  }

  invoice(id: number, input: InvoiceInput, file: File | null): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`${this.base}/${id}/invoice`, invoiceBody(input, file));
  }

  openFile(id: number, fileId: number): void {
    this.http.get(`${this.base}/${id}/files/${fileId}`, { responseType: 'blob' }).subscribe(open);
  }

  openPdf(id: number): void {
    this.http.get(`${this.base}/${id}/pdf`, { responseType: 'blob' }).subscribe(open);
  }
}
