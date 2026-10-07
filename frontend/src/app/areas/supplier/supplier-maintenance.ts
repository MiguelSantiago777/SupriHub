import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { switchMap } from 'rxjs';
import {
  ACCOUNT_TYPES,
  Application,
  BankAccountType,
  CERTIFICATE_BADGE,
  CertificateStatus,
  ChecklistItem,
  RegistrationForm,
  SupplierApi,
} from '../../core/api/supplier.api';
import { digits, formatCep, formatCpf, formatDate, formatPhone, todayIso } from '../../core/format';
import { problemMessage } from '../../core/http/problem';
import { Municipality } from '../../core/models';

const MAX_UPLOAD_MB = 10;

/**
 * Credenciado em dia (Política art. 17, § 2º, 21, § 2º e 22): certidões com validade,
 * troca de documentos, atualização cadastral e pedido de renovação.
 */
@Component({
  selector: 'app-supplier-maintenance',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  template: `
    @let a = app();
    @let m = a.maintenance!;

    <!-- Avisos --------------------------------------------------------------- -->
    @if (m.inactiveSince) {
      <section class="status-card error" role="alert">
        <span class="icon-tile error"><mat-icon aria-hidden="true">pause_circle</mat-icon></span>
        <div>
          <h2>Seu cadastro está inativo temporariamente</h2>
          <p>
            Desde {{ fmtDate(m.inactiveSince) }}, por certidão vencida não regularizada no prazo
            (Política, art. 22). Você não recebe oportunidades nem ordens. Envie a certidão
            atualizada abaixo: a reativação é automática.
          </p>
        </div>
      </section>
    } @else if (m.irregularSince) {
      <section class="status-card warning" role="alert">
        <span class="icon-tile amber"><mat-icon aria-hidden="true">assignment_late</mat-icon></span>
        <div>
          <h2>Há certidão vencida</h2>
          <p>
            Envie a certidão atualizada até <strong>{{ fmtDate(m.regularizationDue) }}</strong> (5
            dias úteis). Sem ela, o cadastro fica inativo temporariamente.
          </p>
        </div>
      </section>
    }

    <!-- Certidões ------------------------------------------------------------ -->
    <section class="card block" aria-labelledby="m-cert">
      <h2 id="m-cert"><mat-icon aria-hidden="true">verified_user</mat-icon> Certidões</h2>
      <p class="muted">
        Mantenha as certidões em dia. Avisamos por e-mail {{ m.alertDays }} dias antes de cada
        vencimento. Para trocar, informe a nova validade e escolha o arquivo (PDF, JPG ou PNG de até
        {{ maxUploadMb }} MB).
      </p>
      <ul class="certs">
        @for (c of m.certificates; track c.type) {
          <li [class.bad]="c.state === 'EXPIRED' || c.state === 'MISSING'">
            <div class="cert-text">
              <span class="cert-name">{{ c.label }}</span>
              <span>
                <span class="badge" [class]="certBadge[c.state]">{{ c.stateLabel }}</span>
                @if (c.validUntil) {
                  <span class="muted"> válida até {{ fmtDate(c.validUntil) }}</span>
                }
                @if (c.verified) {
                  <span class="muted"> · conferida pela Comissão</span>
                }
              </span>
            </div>
            <div class="cert-actions">
              @if (busy() === c.type) {
                <mat-spinner diameter="28" aria-label="Enviando" />
              } @else {
                @if (c.documentId) {
                  <button
                    mat-icon-button
                    type="button"
                    (click)="api.openDocument(c.documentId)"
                    [attr.aria-label]="'Abrir ' + c.label"
                  >
                    <mat-icon>visibility</mat-icon>
                  </button>
                }
                <label class="validity">
                  <span>Nova validade</span>
                  <input
                    type="date"
                    [min]="today"
                    [value]="dates()[c.type] ?? ''"
                    (change)="setDate(c.type, $event)"
                    [attr.aria-label]="'Nova validade: ' + c.label"
                  />
                </label>
                <label class="upload-btn">
                  <input
                    type="file"
                    [accept]="accept"
                    class="visually-hidden"
                    (change)="uploadCertificate(c, $event)"
                  />
                  <mat-icon aria-hidden="true">upload</mat-icon>
                  {{ c.documentId ? 'Enviar nova' : 'Enviar' }}
                </label>
              }
            </div>
          </li>
        }
      </ul>

      @if (otherDocs().length) {
        <details class="others">
          <summary>Trocar outros documentos (comprovante de endereço, conta, alvará…)</summary>
          <ul class="certs">
            @for (item of otherDocs(); track item.type) {
              <li>
                <div class="cert-text">
                  <span class="cert-name">{{ item.label }}</span>
                  <span class="muted">{{
                    item.document ? item.document.filename : 'Não enviado'
                  }}</span>
                </div>
                <div class="cert-actions">
                  @if (busy() === item.type) {
                    <mat-spinner diameter="28" aria-label="Enviando" />
                  } @else {
                    <label class="upload-btn secondary">
                      <input
                        type="file"
                        [accept]="accept"
                        class="visually-hidden"
                        (change)="uploadOther(item, $event)"
                      />
                      <mat-icon aria-hidden="true">sync</mat-icon>
                      {{ item.document ? 'Trocar' : 'Enviar' }}
                    </label>
                  }
                </div>
              </li>
            }
          </ul>
        </details>
      }
    </section>

    <!-- Renovação ------------------------------------------------------------ -->
    @if (m.renewalRequestedAt || m.renewalOpen || m.renewalReturnMessage) {
      <section class="card block" aria-labelledby="m-ren">
        <h2 id="m-ren"><mat-icon aria-hidden="true">autorenew</mat-icon> Renovação</h2>
        @if (m.renewalRequestedAt) {
          <p class="alert alert-info">
            Seu pedido de renovação está com a Comissão. O credenciamento continua valendo até
            {{ fmtDate(a.accreditationValidUntil) }}.
          </p>
        } @else {
          @if (m.renewalReturnMessage) {
            <div class="alert alert-warning" role="alert">
              <strong>A Comissão devolveu o pedido:</strong> {{ m.renewalReturnMessage }}
            </div>
          }
          <p>
            O credenciamento vale até <strong>{{ fmtDate(a.accreditationValidUntil) }}</strong
            >. Confira as certidões e os seus dados acima e peça a renovação por mais 24 meses
            (Política, art. 22).
          </p>
          <mat-checkbox
            [checked]="renewalDeclarations()"
            (change)="renewalDeclarations.set($event.checked)"
          >
            Confirmo, de novo, a Declaração Unificada e o Termo de Adesão (Anexos III e V) e que
            mantenho as condições de habilitação.
          </mat-checkbox>
          @if (renewalError()) {
            <p class="alert alert-error" role="alert">{{ renewalError() }}</p>
          }
          <div class="actions">
            <button
              mat-flat-button
              type="button"
              [disabled]="!renewalDeclarations() || busy() === 'renewal'"
              (click)="requestRenewal()"
            >
              <mat-icon aria-hidden="true">autorenew</mat-icon> Pedir renovação
            </button>
          </div>
        }
      </section>
    }

    <!-- Atualização cadastral ---------------------------------------------- -->
    <section class="card block" aria-labelledby="m-dados">
      <h2 id="m-dados"><mat-icon aria-hidden="true">edit</mat-icon> Atualizar dados</h2>
      @if (!editing()) {
        <p class="muted">
          Mudou o endereço, o telefone, o e-mail, o representante ou a conta? Atualize aqui (Anexo
          V). A Comissão é avisada. Para mudar linhas, territórios ou o tipo de negócio, fale com a
          Comissão.
        </p>
        <button mat-stroked-button type="button" (click)="startEditing()">
          <mat-icon aria-hidden="true">edit</mat-icon> Atualizar dados
        </button>
      } @else {
        <form [formGroup]="form" (ngSubmit)="saveRegistration()">
          <h3>Endereço</h3>
          <div class="row">
            <mat-form-field>
              <mat-label>CEP</mat-label>
              <input
                matInput
                formControlName="cep"
                inputmode="numeric"
                maxlength="9"
                (blur)="mask('cep')"
              />
            </mat-form-field>
            <mat-form-field class="span-2">
              <mat-label>Rua, avenida…</mat-label>
              <input matInput formControlName="street" autocomplete="address-line1" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Número</mat-label>
              <input matInput formControlName="addressNumber" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Complemento (opcional)</mat-label>
              <input matInput formControlName="addressComplement" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Bairro</mat-label>
              <input matInput formControlName="district" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Município (RJ)</mat-label>
              <mat-select formControlName="municipalityId">
                @for (mu of municipalities(); track mu.id) {
                  <mat-option [value]="mu.id">{{ mu.name }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>

          <h3>Contato</h3>
          <div class="row">
            <mat-form-field>
              <mat-label>Telefone com WhatsApp</mat-label>
              <input
                matInput
                formControlName="phone"
                inputmode="tel"
                maxlength="15"
                (blur)="mask('phone')"
              />
            </mat-form-field>
            <mat-form-field>
              <mat-label>E-mail de contato</mat-label>
              <input matInput type="email" formControlName="contactEmail" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Site, Instagram ou portfólio (opcional)</mat-label>
              <input matInput formControlName="website" />
            </mat-form-field>
          </div>

          @if (a.supplierType !== 'INDIVIDUAL') {
            <h3>Titular ou representante legal</h3>
            <div class="row">
              <mat-form-field class="span-2">
                <mat-label>Nome</mat-label>
                <input matInput formControlName="representativeName" />
              </mat-form-field>
              <mat-form-field>
                <mat-label>CPF</mat-label>
                <input
                  matInput
                  formControlName="representativeCpf"
                  inputmode="numeric"
                  maxlength="14"
                  (blur)="mask('representativeCpf')"
                />
              </mat-form-field>
              <mat-form-field>
                <mat-label>Cargo (opcional)</mat-label>
                <input matInput formControlName="representativeRole" />
              </mat-form-field>
            </div>
          }

          <h3>Conta para pagamento</h3>
          <p class="muted small">A conta precisa ser do próprio credenciado (Política, art. 19).</p>
          <div class="row">
            <mat-form-field>
              <mat-label>Banco</mat-label>
              <input matInput formControlName="bankName" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Agência</mat-label>
              <input matInput formControlName="bankBranch" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Conta</mat-label>
              <input matInput formControlName="bankAccount" />
            </mat-form-field>
            <mat-form-field>
              <mat-label>Tipo de conta</mat-label>
              <mat-select formControlName="bankAccountType">
                @for (t of accountTypes; track t.value) {
                  <mat-option [value]="t.value">{{ t.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Chave Pix (opcional)</mat-label>
              <input matInput formControlName="pixKey" />
            </mat-form-field>
            <mat-form-field class="span-2">
              <mat-label>Titular da conta</mat-label>
              <input matInput formControlName="accountHolder" />
            </mat-form-field>
          </div>

          @if (formError()) {
            <p class="alert alert-error" role="alert">{{ formError() }}</p>
          }
          <div class="actions">
            <button mat-button type="button" (click)="editing.set(false)">Cancelar</button>
            <button mat-flat-button type="submit" [disabled]="busy() === 'registration'">
              <mat-icon aria-hidden="true">save</mat-icon> Salvar alterações
            </button>
          </div>
        </form>
      }
    </section>
  `,
  styles: `
    :host {
      display: grid;
      gap: 1rem;
    }
    .status-card {
      display: flex;
      gap: 1rem;
      align-items: flex-start;
      padding: 1.25rem 1.5rem;
      border-radius: var(--sh-radius);
      border: 1px solid var(--sh-border);
      box-shadow: var(--sh-shadow-sm);
    }
    .status-card h2 {
      font-size: 1.25rem;
      margin: 0 0 0.35rem;
    }
    .status-card p {
      margin: 0;
    }
    .status-card.warning {
      background: var(--sh-amber-soft);
      border-color: #f0d48f;
    }
    .status-card.error {
      background: var(--sh-error-soft);
      border-color: #f3b8b2;
    }
    .status-card .icon-tile {
      background: #fff;
    }
    .icon-tile.error {
      color: var(--sh-error);
    }
    h2 {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 1.2rem;
      margin: 0 0 0.75rem;
    }
    h2 mat-icon {
      color: var(--sh-primary);
    }
    h3 {
      font-size: 1.02rem;
      margin: 1rem 0 0.5rem;
    }
    .small {
      font-size: 0.9rem;
      margin-top: -0.25rem;
    }
    .certs {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 0.5rem;
    }
    .certs li {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem;
      padding: 0.75rem;
      border: 1px solid var(--sh-border);
      border-radius: var(--sh-radius-sm);
    }
    .certs li.bad {
      border-color: var(--sh-error);
      background: var(--sh-error-soft);
    }
    .cert-text {
      flex: 1 1 260px;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .cert-name {
      font-weight: 700;
    }
    .cert-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
    .validity {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.9rem;
      font-weight: 700;
      color: var(--sh-text-muted);
    }
    .validity input {
      min-height: 40px;
      padding: 0 0.6rem;
      border: 1px solid var(--sh-border);
      border-radius: var(--sh-radius-sm);
      font: inherit;
      font-weight: 400;
      color: var(--sh-text);
      background: var(--sh-surface);
    }
    .upload-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      min-height: 44px;
      padding: 0 1.1rem;
      border-radius: 999px;
      background: var(--sh-primary);
      color: #fff;
      font-weight: 700;
      cursor: pointer;
    }
    .upload-btn.secondary {
      background: transparent;
      color: var(--sh-primary);
      border: 1px solid var(--sh-primary);
    }
    .upload-btn:focus-within {
      outline: 3px solid var(--sh-focus, var(--sh-primary));
      outline-offset: 2px;
    }
    .others {
      margin-top: 1rem;
    }
    .others summary {
      cursor: pointer;
      font-weight: 700;
      color: var(--sh-primary);
      margin-bottom: 0.75rem;
    }
    .row {
      display: grid;
      gap: 0 1rem;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    }
    @media (min-width: 700px) {
      .row .span-2 {
        grid-column: span 2;
      }
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 1rem;
    }
  `,
})
export class SupplierMaintenance {
  protected readonly api = inject(SupplierApi);
  private readonly snack = inject(MatSnackBar);

  readonly app = input.required<Application>();
  readonly municipalities = input<Municipality[]>([]);
  /** Requerimento atualizado depois de cada ação. */
  readonly changed = output<Application>();

  protected readonly certBadge = CERTIFICATE_BADGE;
  protected readonly accountTypes = ACCOUNT_TYPES;
  protected readonly fmtDate = formatDate;
  protected readonly today = todayIso();
  protected readonly maxUploadMb = MAX_UPLOAD_MB;
  protected readonly accept = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';

  protected readonly busy = signal<string | null>(null);
  protected readonly dates = signal<Record<string, string>>({});
  protected readonly editing = signal(false);
  protected readonly formError = signal('');
  protected readonly renewalDeclarations = signal(false);
  protected readonly renewalError = signal('');

  /** Documentos que não são certidão, para troca (Anexo V). */
  protected readonly otherDocs = computed<ChecklistItem[]>(() =>
    this.app().checklist.filter((c) => !c.certificate && c.requirement !== 'NOT_APPLICABLE'),
  );

  protected readonly form = new FormGroup({
    street: new FormControl<string | null>(null),
    addressNumber: new FormControl<string | null>(null),
    addressComplement: new FormControl<string | null>(null),
    district: new FormControl<string | null>(null),
    municipalityId: new FormControl<number | null>(null),
    cep: new FormControl<string | null>(null),
    phone: new FormControl<string | null>(null),
    contactEmail: new FormControl<string | null>(null),
    website: new FormControl<string | null>(null),
    representativeName: new FormControl<string | null>(null),
    representativeCpf: new FormControl<string | null>(null),
    representativeRole: new FormControl<string | null>(null),
    bankName: new FormControl<string | null>(null),
    bankBranch: new FormControl<string | null>(null),
    bankAccount: new FormControl<string | null>(null),
    bankAccountType: new FormControl<BankAccountType | null>(null),
    pixKey: new FormControl<string | null>(null),
    accountHolder: new FormControl<string | null>(null),
  });

  protected setDate(type: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.dates.update((d) => ({ ...d, [type]: value }));
  }

  protected uploadCertificate(c: CertificateStatus, event: Event): void {
    const file = this.pick(event);
    if (!file) {
      return;
    }
    const validUntil = this.dates()[c.type];
    if (!validUntil) {
      this.snack.open('Informe primeiro a nova validade da certidão.', 'OK', { duration: 5000 });
      return;
    }
    this.send(c.type, file, validUntil, 'Certidão enviada. A Comissão foi avisada.');
  }

  protected uploadOther(item: ChecklistItem, event: Event): void {
    const file = this.pick(event);
    if (file) {
      this.send(item.type, file, null, 'Documento atualizado. A Comissão foi avisada.');
    }
  }

  private pick(event: Event): File | null {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (file && file.size > MAX_UPLOAD_MB * 1024 * 1024) {
      this.snack.open(`O arquivo tem mais de ${MAX_UPLOAD_MB} MB.`, 'OK', { duration: 5000 });
      return null;
    }
    return file;
  }

  private send(type: string, file: File, validUntil: string | null, done: string): void {
    this.busy.set(type);
    this.api
      .upload(type, file, validUntil)
      .pipe(switchMap(() => this.api.get()))
      .subscribe({
        next: (app) => {
          this.busy.set(null);
          this.dates.update((d) => ({ ...d, [type]: '' }));
          this.changed.emit(app);
          this.snack.open(done, 'OK', { duration: 4000 });
        },
        error: (e: unknown) => {
          this.busy.set(null);
          this.snack.open(problemMessage(e), 'OK', { duration: 6000 });
        },
      });
  }

  protected requestRenewal(): void {
    const a = this.app();
    this.busy.set('renewal');
    this.renewalError.set('');
    this.api.requestRenewal(this.renewalDeclarations(), a.version).subscribe({
      next: (app) => {
        this.busy.set(null);
        // Se a Comissão devolver, a confirmação precisa ser marcada de novo.
        this.renewalDeclarations.set(false);
        this.changed.emit(app);
        this.snack.open('Pedido de renovação enviado à Comissão.', 'OK', { duration: 5000 });
      },
      error: (e: unknown) => {
        this.busy.set(null);
        this.renewalError.set(problemMessage(e));
      },
    });
  }

  protected startEditing(): void {
    const a = this.app();
    this.form.reset({
      street: a.street,
      addressNumber: a.addressNumber,
      addressComplement: a.addressComplement,
      district: a.district,
      municipalityId: a.municipalityId,
      cep: a.cep ? formatCep(a.cep) : null,
      phone: a.phone ? formatPhone(a.phone) : null,
      contactEmail: a.contactEmail,
      website: a.website,
      representativeName: a.representativeName,
      representativeCpf: a.representativeCpf ? formatCpf(a.representativeCpf) : null,
      representativeRole: a.representativeRole,
      bankName: a.bankName,
      bankBranch: a.bankBranch,
      bankAccount: a.bankAccount,
      bankAccountType: a.bankAccountType,
      pixKey: a.pixKey,
      accountHolder: a.accountHolder,
    });
    this.formError.set('');
    this.editing.set(true);
  }

  protected mask(field: 'cep' | 'phone' | 'representativeCpf'): void {
    const c = this.form.controls[field];
    if (!c.value) {
      return;
    }
    const formatted =
      field === 'cep'
        ? formatCep(c.value)
        : field === 'phone'
          ? formatPhone(c.value)
          : formatCpf(c.value);
    if (formatted !== c.value) {
      c.setValue(formatted);
    }
  }

  protected saveRegistration(): void {
    const v = this.form.getRawValue();
    const text = (s: string | null) => (s && s.trim() ? s.trim() : null);
    const nums = (s: string | null) => (digits(s) ? digits(s) : null);
    const body: RegistrationForm = {
      street: text(v.street),
      addressNumber: text(v.addressNumber),
      addressComplement: text(v.addressComplement),
      district: text(v.district),
      municipalityId: v.municipalityId,
      cep: nums(v.cep),
      phone: nums(v.phone),
      contactEmail: text(v.contactEmail),
      website: text(v.website),
      representativeName: text(v.representativeName),
      representativeCpf: nums(v.representativeCpf),
      representativeRole: text(v.representativeRole),
      bankName: text(v.bankName),
      bankBranch: text(v.bankBranch),
      bankAccount: text(v.bankAccount),
      bankAccountType: v.bankAccountType,
      pixKey: text(v.pixKey),
      accountHolder: text(v.accountHolder),
    };
    this.busy.set('registration');
    this.formError.set('');
    this.api.updateRegistration(body, this.app().version).subscribe({
      next: (app) => {
        this.busy.set(null);
        this.editing.set(false);
        this.changed.emit(app);
        this.snack.open('Dados atualizados. A Comissão foi avisada.', 'OK', { duration: 4000 });
      },
      error: (e: unknown) => {
        this.busy.set(null);
        this.formError.set(problemMessage(e));
      },
    });
  }
}
