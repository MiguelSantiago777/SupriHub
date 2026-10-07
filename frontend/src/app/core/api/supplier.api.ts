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
  /** Validade da certidão (só certidões). */
  validUntil: string | null;
  /** Conferida pela Comissão no sítio oficial. */
  verifiedAt: string | null;
}

export interface ChecklistItem {
  type: string;
  label: string;
  requirement: Requirement;
  /** Certidão com validade (federal, FGTS, CNDT, municipal). */
  certificate: boolean;
  document: SupplierDocument | null;
}

export type CertificateState = 'VALID' | 'EXPIRING' | 'EXPIRED' | 'NO_DATE' | 'MISSING';

export const CERTIFICATE_BADGE: Record<CertificateState, string> = {
  VALID: 'badge-success',
  EXPIRING: 'badge-warning',
  EXPIRED: 'badge-error',
  NO_DATE: 'badge-neutral',
  MISSING: 'badge-error',
};

export interface CertificateStatus {
  type: string;
  label: string;
  documentId: number | null;
  validUntil: string | null;
  state: CertificateState;
  stateLabel: string;
  verified: boolean;
}

/** Manutenção do credenciado: certidões, inativação, reconferência e renovação. */
export interface Maintenance {
  certificates: CertificateStatus[];
  regular: boolean;
  irregularSince: string | null;
  regularizationDue: string | null;
  inactiveSince: string | null;
  deregistrationAlerted: boolean;
  lastRecheckOn: string | null;
  nextRecheckDue: string | null;
  recheckDue: boolean;
  renewalOpensOn: string | null;
  renewalOpen: boolean;
  renewalRequestedAt: string | null;
  renewalReturnMessage: string | null;
  alertDays: number;
}

export type MaintenanceView = 'CERTIFICATES' | 'RECHECK' | 'RENEWAL' | 'INACTIVE';

export interface MaintenanceRow {
  id: number;
  protocol: string | null;
  name: string;
  supplierTypeLabel: string | null;
  documentMasked: string | null;
  accreditationValidUntil: string | null;
  maintenance: Maintenance;
}

/** Dados que o credenciado atualiza sem novo requerimento (Anexo V). */
export interface RegistrationForm {
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
  bankName: string | null;
  bankBranch: string | null;
  bankAccount: string | null;
  bankAccountType: BankAccountType | null;
  pixKey: string | null;
  accountHolder: string | null;
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
  publishWomanLed: boolean;
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
  appealDeadline: string | null;
  canAppeal: boolean;
  appeal: Appeal | null;
  editable: boolean;
  checklist: ChecklistItem[];
  history: HistoryEntry[];
  pendingItems: Issue[];
  /** Só para habilitados. */
  maintenance: Maintenance | null;
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

export type AppealStatus = 'FILED' | 'FORWARDED' | 'GRANTED' | 'DENIED';
export type AppealStage = 'COMMISSION' | 'BOARD' | 'DECIDED';

export const APPEAL_BADGE: Record<AppealStatus, string> = {
  FILED: 'badge-warning',
  FORWARDED: 'badge-info',
  GRANTED: 'badge-success',
  DENIED: 'badge-error',
};

export interface Appeal {
  id: number;
  status: AppealStatus;
  statusLabel: string;
  reasons: string;
  request: string;
  attachment: { filename: string; contentType: string; sizeBytes: number } | null;
  filedAt: string;
  reconsiderationDue: string;
  commissionReason: string | null;
  commissionDecidedAt: string | null;
  boardDue: string | null;
  boardReason: string | null;
  boardDecidedAt: string | null;
  version: number;
}

export interface AppealSummary {
  id: number;
  status: AppealStatus;
  statusLabel: string;
  applicationId: number;
  protocol: string | null;
  legalName: string | null;
  supplierTypeLabel: string | null;
  filedAt: string;
  dueDate: string | null;
  overdue: boolean;
}

export interface AppealDetail {
  appeal: Appeal;
  application: Application;
}

/** Credenciado vigente — cadastro de uso interno da equipe do Instituto. */
export interface AccreditedSupplier {
  id: number;
  name: string;
  legalName: string | null;
  supplierTypeLabel: string | null;
  documentMasked: string | null;
  municipalityName: string | null;
  phone: string | null;
  contactEmail: string | null;
  lines: ApplicationLine[];
  territories: Territory[];
  womanLed: boolean;
  validUntil: string;
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

  /** Certidões vão com a validade (AAAA-MM-DD). */
  upload(type: string, file: File, validUntil: string | null = null): Observable<SupplierDocument> {
    const body = new FormData();
    body.append('file', file);
    if (validUntil) {
      body.append('validUntil', validUntil);
    }
    return this.http.post<SupplierDocument>(`${this.base}/documents/${type}`, body);
  }

  setValidity(documentId: number, validUntil: string): Observable<Application> {
    return this.http.put<Application>(`${this.base}/documents/${documentId}/validity`, {
      validUntil,
    });
  }

  updateRegistration(form: RegistrationForm, version: number): Observable<Application> {
    return this.http.put<Application>(`${this.base}/registration`, { ...form, version });
  }

  requestRenewal(declarationsAccepted: boolean, version: number): Observable<Application> {
    return this.http.post<Application>(`${this.base}/renewal`, { declarationsAccepted, version });
  }

  deleteDocument(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/documents/${id}`);
  }

  openDocument(id: number): void {
    this.http
      .get(`${this.base}/documents/${id}/file`, { responseType: 'blob' })
      .subscribe(openBlob);
  }

  appeal(reasons: string, request: string, file: File | null): Observable<Application> {
    const body = new FormData();
    body.append('reasons', reasons);
    body.append('request', request);
    if (file) {
      body.append('file', file);
    }
    return this.http.post<Application>(`${this.base}/appeal`, body);
  }

  openAppealAttachment(): void {
    this.http.get(`${this.base}/appeal/attachment`, { responseType: 'blob' }).subscribe(openBlob);
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

  // --- Manutenção do cadastro ----------------------------------------------

  maintenance(view: MaintenanceView): Observable<MaintenanceRow[]> {
    return this.http.get<MaintenanceRow[]>('/api/commission/maintenance', {
      params: new HttpParams().set('view', view),
    });
  }

  maintenanceCounts(): Observable<Record<MaintenanceView, number>> {
    return this.http.get<Record<MaintenanceView, number>>('/api/commission/maintenance/counts');
  }

  /** Certidão obtida pela Comissão no sítio oficial (entra conferida). */
  uploadCertificate(
    id: number,
    type: string,
    file: File,
    validUntil: string,
  ): Observable<Application> {
    const body = new FormData();
    body.append('file', file);
    body.append('validUntil', validUntil);
    return this.http.post<Application>(`${this.base}/${id}/documents/${type}`, body);
  }

  setValidity(id: number, documentId: number, validUntil: string): Observable<Application> {
    return this.http.put<Application>(`${this.base}/${id}/documents/${documentId}/validity`, {
      validUntil,
    });
  }

  recheck(id: number, message: string, version: number): Observable<Application> {
    return this.http.post<Application>(`${this.base}/${id}/recheck`, { message, version });
  }

  renew(id: number, message: string, version: number): Observable<Application> {
    return this.http.post<Application>(`${this.base}/${id}/renew`, { message, version });
  }

  returnRenewal(id: number, message: string, version: number): Observable<Application> {
    return this.http.post<Application>(`${this.base}/${id}/return-renewal`, { message, version });
  }
}

@Injectable({ providedIn: 'root' })
export class AppealApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/appeals';

  list(stage: AppealStage, page = 0): Observable<Page<AppealSummary>> {
    const params = new HttpParams().set('stage', stage).set('page', page).set('size', 20);
    return this.http.get<Page<AppealSummary>>(this.base, { params });
  }

  counts(): Observable<Record<AppealStage, number>> {
    return this.http.get<Record<AppealStage, number>>(`${this.base}/counts`);
  }

  get(id: number): Observable<AppealDetail> {
    return this.http.get<AppealDetail>(`${this.base}/${id}`);
  }

  openAttachment(id: number): void {
    this.http.get(`${this.base}/${id}/attachment`, { responseType: 'blob' }).subscribe(openBlob);
  }

  openDocument(id: number, documentId: number): void {
    this.http
      .get(`${this.base}/${id}/documents/${documentId}/file`, { responseType: 'blob' })
      .subscribe(openBlob);
  }

  reconsider(id: number, reason: string, version: number): Observable<AppealDetail> {
    return this.http.post<AppealDetail>(`${this.base}/${id}/reconsider`, { reason, version });
  }

  forward(id: number, reason: string, version: number): Observable<AppealDetail> {
    return this.http.post<AppealDetail>(`${this.base}/${id}/forward`, { reason, version });
  }

  boardDecision(
    id: number,
    granted: boolean,
    reason: string,
    version: number,
  ): Observable<AppealDetail> {
    return this.http.post<AppealDetail>(`${this.base}/${id}/board-decision`, {
      granted,
      reason,
      version,
    });
  }
}

@Injectable({ providedIn: 'root' })
export class AccreditedSupplierApi {
  private readonly http = inject(HttpClient);

  search(
    line: string,
    territory: Territory | null,
    q: string,
    page = 0,
  ): Observable<Page<AccreditedSupplier>> {
    let params = new HttpParams().set('page', page).set('size', 24);
    if (line) {
      params = params.set('line', line);
    }
    if (territory) {
      params = params.set('territory', territory);
    }
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<Page<AccreditedSupplier>>('/api/suppliers', { params });
  }
}
