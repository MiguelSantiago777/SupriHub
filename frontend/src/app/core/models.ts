/** Perfis de acesso (espelham o enum AppRole do backend). */
export type AppRole =
  'SUPPLIER' | 'COMMISSION' | 'DEMAND_MANAGER' | 'FINANCE' | 'INTERNAL_CONTROL' | 'BOARD' | 'ADMIN';

export const ROLE_LABELS: Record<AppRole, string> = {
  SUPPLIER: 'Fornecedor',
  COMMISSION: 'Comissão de Credenciamento',
  DEMAND_MANAGER: 'Gestor da demanda',
  FINANCE: 'Financeiro',
  INTERNAL_CONTROL: 'Controle Interno',
  BOARD: 'Diretoria',
  ADMIN: 'Administração (TI)',
};

export interface Me {
  id: number;
  fullName: string;
  email: string;
  roles: AppRole[];
  emailVerified: boolean;
  /** Login pede código de 6 dígitos por e-mail. */
  twoFactor: boolean;
  /** Equipe interna: o código é obrigatório e não pode ser desligado. */
  twoFactorMandatory: boolean;
}

/** Senha conferiu; falta o código enviado por e-mail (segunda etapa do login). */
export interface CodeRequiredResponse {
  codeRequired: true;
  challenge: string;
  sentTo: string;
}

export interface SessionResponse {
  accessToken: string;
  expiresAt: string;
  user: Me;
}

export interface MessageResponse {
  message: string;
}

/** RFC 7807 com extensões do SupriHub. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  errors?: { field: string; message: string }[];
}

export interface SupplyLine {
  code: string;
  name: string;
  shortName: string;
  scope: string;
  cnaeReference: string;
  allowsIndividual: boolean;
  allowsMediumLarge: boolean;
  active: boolean;
  version: number;
}

export interface Municipality {
  id: number;
  ibgeCode: string;
  name: string;
  uf: string;
  metropolitanRegion: boolean;
}

export interface Parameter {
  key: string;
  value: string;
  valueType: 'DECIMAL' | 'INTEGER' | 'BOOLEAN' | 'TEXT';
  category: string;
  description: string;
  source?: string;
  updatedAt: string;
}

export interface ParameterHistory {
  oldValue?: string;
  newValue: string;
  reason?: string;
  changedBy?: number;
  changedAt: string;
}

export interface UserSummary {
  id: number;
  fullName: string;
  email: string;
  roles: AppRole[];
  enabled: boolean;
  emailVerified: boolean;
  lastLoginAt?: string;
  createdAt: string;
  version: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface BandResult {
  band: 'FAIXA_1' | 'FAIXA_2' | 'FAIXA_3';
  label: string;
  selectionMode: string;
  estimatedValue: number;
  band1UpperLimit: number;
  band2UpperLimit: number;
  referenceYear: number;
}
