import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../models';

export type SupplierType = 'MEI' | 'ME' | 'EPP' | 'COOPERATIVE' | 'INDIVIDUAL';
export type ApplicationStatus =
  'DRAFT' | 'SUBMITTED' | 'ADJUSTMENT_REQUESTED' | 'APPROVED' | 'REJECTED';
export type Territory = 'T1' | 'T2' | 'T3';
export type Race = 'PRETA' | 'PARDA' | 'INDIGENA' | 'BRANCA' | 'AMARELA' | 'NAO_INFORMADA';
export type BankAccountType = 'CHECKING' | 'SAVINGS' | 'PAYMENT';
export type Requirement = 'REQUIRED' | 'OPTIONAL' | 'NOT_APPLICABLE';

export const SUPPLIER_TYPES: { value: SupplierType; label: string; hint: string }[] = [
  { value: 'MEI', label: 'MEI', hint: 'Microempreendedor Individual' },
  { value: 'ME', label: 'Microempresa (ME)', hint: 'Faturamento até R$ 360 mil/ano' },
  { value: 'EPP', label: 'Empresa de Pequeno Porte (EPP)', hint: 'Até R$ 4,8 milhões/ano' },
  { value: 'COOPERATIVE', label: 'Cooperativa', hint: 'Sociedade cooperativa' },
  { value: 'INDIVIDUAL', label: 'Autônomo (pessoa física)', hint: 'Sem CNPJ, com CPF' },
];

export const TERRITORIES: { value: Territory; label: string; hint: string }[] = [
  { value: 'T1', label: 'T1 — Mesquita', hint: 'e município onde executa projetos do Instituto' },
  { value: 'T2', label: 'T2 — Região Metropolitana do Rio', hint: '22 municípios da RMRJ' },
  { value: 'T3', label: 'T3 — Demais municípios do Estado do RJ', hint: 'interior do estado' },
];

export const RACES: { value: Race; label: string }[] = [
  { value: 'PRETA', label: 'Preta' },
  { value: 'PARDA', label: 'Parda' },
  { value: 'INDIGENA', label: 'Indígena' },
  { value: 'BRANCA', label: 'Branca' },
  { value: 'AMARELA', label: 'Amarela' },
  { value: 'NAO_INFORMADA', label: 'Prefiro não informar' },
];

export const ACCOUNT_TYPES: { value: BankAccountType; label: string }[] = [
  { value: 'CHECKING', label: 'Corrente' },
  { value: 'SAVINGS', label: 'Poupança' },
  { value: 'PAYMENT', label: 'Conta de pagamento' },
];

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  DRAFT: 'Em preenchimento',
  SUBMITTED: 'Em análise',
  ADJUSTMENT_REQUESTED: 'Ajuste solicitado',
  APPROVED: 'Habilitado',
  REJECTED: 'Inabilitado',
};

export const STATUS_BADGE: Record<ApplicationStatus, string> = {
  DRAFT: 'badge-neutral',
  SUBMITTED: 'badge-info',
  ADJUSTMENT_REQUESTED: 'badge-warning',
  APPROVED: 'badge-success',
  REJECTED: 'badge-error',
};

export interface SupplierDocument {
  id: number;
  type: string;
  label: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface ChecklistItem {
  type: string;
  label: string;
  requirement: Requirement;
  document: SupplierDocument | null;
}

export interface HistoryEntry {
  status: ApplicationStatus;
  statusLabel: string;
  message: string | null;
  actor: string;
  occurredAt: string;
}

export interface Issue {
  field: string;
  message: string;
}

export interface ApplicationLine {
  code: string;
  shortName: string;
  allowsIndividual: boolean;
}

/** Campos editáveis do Anexo II (corpo do PUT). */
export interface ApplicationForm {
  supplierType: SupplierType | null;
  legalName: string | null;
  tradeName: string | null;
  documentNumber: string | null;
  birthDate: string | null;
  street: string | null;
  addressNumber: string | null;
  addressComplement: string | null;
  district: string | null;
  municipalityId: number | null;
  cep: string | null;
  phone: string | null;
  contactEmail: string | null;
  website: string | null;
  representativeName: string | null;
  representativeCpf: string | null;
  representativeRole: string | null;
  lineCodes: string[];
  linesDescription: string | null;
  territories: Territory[];
  territoriesNote: string | null;
  bankName: string | null;
  bankBranch: string | null;
  bankAccount: string | null;
  bankAccountType: BankAccountType | null;
  pixKey: string | null;
  accountHolder: string | null;
  womanLed: boolean;
  race: Race | null;
  cadUnico: boolean;
  nis: string | null;
  declarationsAccepted: boolean;
}

export interface Application extends Omit<ApplicationForm, 'lineCodes'> {
  id: number;
  status: ApplicationStatus;
  statusLabel: string;
  protocol: string | null;
  supplierTypeLabel: string | null;
  municipalityName: string | null;
  lines: ApplicationLine[];
  declarationsAcceptedAt: string | null;
  submittedAt: string | null;
  analysisDueDate: string | null;
  adjustmentMessage: string | null;
  adjustmentDueDate: string | null;
  decidedAt: string | null;
  decisionReason: string | null;
  accreditationValidUntil: string | null;
  editable: boolean;
  checklist: ChecklistItem[];
  history: HistoryEntry[];
  pendingItems: Issue[];
  version: number;
}

export interface ApplicationSummary {
  id: number;
  protocol: string | null;
  status: ApplicationStatus;
  legalName: string | null;
  tradeName: string | null;
  supplierType: SupplierType | null;
  supplierTypeLabel: string | null;
  documentMasked: string | null;
  municipalityName: string | null;
  lineCodes: string[];
  womanLed: boolean;
  submittedAt: string | null;
  analysisDueDate: string | null;
  adjustmentDueDate: string | null;
  accreditationValidUntil: string | null;
  overdue: boolean;
}

/** Abre um arquivo protegido (precisa do token) numa nova aba. */
function openBlob(blob: Blob): void {
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank', 'noopener');
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

@Injectable({ providedIn: 'root' })
export class SupplierApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/supplier/application';

  get(): Observable<Application> {
    return this.http.get<Application>(this.base);
  }

  save(form: ApplicationForm, version: number): Observable<Application> {
    return this.http.put<Application>(this.base, { ...form, version });
  }

  upload(type: string, file: File): Observable<SupplierDocument> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<SupplierDocument>(`${this.base}/documents/${type}`, body);
  }

  deleteDocument(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/documents/${id}`);
  }

  openDocument(id: number): void {
    this.http
      .get(`${this.base}/documents/${id}/file`, { responseType: 'blob' })
      .subscribe(openBlob);
  }

  submit(version: number): Observable<Application> {
    return this.http.post<Application>(`${this.base}/submit`, { version });
  }
}

@Injectable({ providedIn: 'root' })
export class ReviewApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/commission/applications';

  list(status: ApplicationStatus, q: string, page = 0): Observable<Page<ApplicationSummary>> {
    let params = new HttpParams().set('status', status).set('page', page).set('size', 20);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<Page<ApplicationSummary>>(this.base, { params });
  }

  counts(): Observable<Record<ApplicationStatus, number>> {
    return this.http.get<Record<ApplicationStatus, number>>(`${this.base}/counts`);
  }

  get(id: number): Observable<Application> {
    return this.http.get<Application>(`${this.base}/${id}`);
  }

  openDocument(applicationId: number, documentId: number): void {
    this.http
      .get(`${this.base}/${applicationId}/documents/${documentId}/file`, { responseType: 'blob' })
      .subscribe(openBlob);
  }

  approve(id: number, message: string, version: number): Observable<Application> {
    return this.http.post<Application>(`${this.base}/${id}/approve`, { message, version });
  }

  requestAdjustment(id: number, message: string, version: number): Observable<Application> {
    return this.http.post<Application>(`${this.base}/${id}/request-adjustment`, {
      message,
      version,
    });
  }

  reject(id: number, message: string, version: number): Observable<Application> {
    return this.http.post<Application>(`${this.base}/${id}/reject`, { message, version });
  }
}
