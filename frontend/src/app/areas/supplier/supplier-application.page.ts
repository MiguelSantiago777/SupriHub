import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { AreaTabs } from '../shared/area-tabs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { EMPTY, forkJoin, Observable, of, switchMap } from 'rxjs';
import { CatalogApi } from '../../core/api/catalog.api';
import {
  ACCOUNT_TYPES,
  Application,
  ApplicationForm,
  ChecklistItem,
  Issue,
  RACES,
  APPEAL_BADGE,
  STATUS_BADGE,
  SUPPLIER_TYPES,
  SupplierApi,
  SupplierType,
  Territory,
  TERRITORIES,
  BankAccountType,
  Race,
} from '../../core/api/supplier.api';
import { AuthService } from '../../core/auth/auth.service';
import {
  digits,
  formatCep,
  formatCpf,
  formatDate,
  formatDocument,
  formatPhone,
  formatSize,
} from '../../core/format';
import { problemMessage, toProblem } from '../../core/http/problem';
import { Municipality, SupplyLine } from '../../core/models';
import { ApplicationView } from './application-view';

interface Section {
  id: string;
  title: string;
  icon: string;
  fields: string[];
}

const SECTIONS: Section[] = [
  { id: 'tipo', title: 'Tipo de negócio', icon: 'storefront', fields: ['supplierType'] },
  {
    id: 'identificacao',
    title: 'Identificação e contato',
    icon: 'badge',
    fields: [
      'legalName',
      'documentNumber',
      'birthDate',
      'street',
      'addressNumber',
      'district',
      'municipalityId',
      'cep',
      'phone',
      'contactEmail',
      'representativeName',
      'representativeCpf',
    ],
  },
  {
    id: 'linhas',
    title: 'Linhas e territórios',
    icon: 'category',
    fields: ['lineCodes', 'linesDescription', 'territories'],
  },
  {
    id: 'pagamento',
    title: 'Dados para pagamento',
    icon: 'account_balance',
    fields: ['bankName', 'bankBranch', 'bankAccount', 'accountHolder'],
  },
  { id: 'equidade', title: 'Equidade (facultativo)', icon: 'diversity_3', fields: ['nis'] },
  { id: 'documentos', title: 'Documentos', icon: 'folder_open', fields: ['documents.'] },
  {
    id: 'envio',
    title: 'Declarações e envio',
    icon: 'task_alt',
    fields: ['declarationsAccepted'],
  },
];

const MAX_UPLOAD_MB = 10;
const ACCEPT = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';

@Component({
  selector: 'app-supplier-application-page',
  imports: [
    AreaTabs,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatRadioModule,
    MatSelectModule,
    ApplicationView,
  ],
  templateUrl: './supplier-application.page.html',
  styleUrls: ['./supplier-application.page.scss', './supplier-appeal.scss'],
})
export class SupplierApplicationPage implements OnInit {
  private readonly api = inject(SupplierApi);
  private readonly catalog = inject(CatalogApi);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  protected readonly sections = SECTIONS;
  protected readonly supplierTypes = SUPPLIER_TYPES;
  protected readonly territoryOptions = TERRITORIES;
  protected readonly races = RACES;
  protected readonly accountTypes = ACCOUNT_TYPES;
  protected readonly badge = STATUS_BADGE;
  protected readonly appealBadge = APPEAL_BADGE;
  protected readonly accept = ACCEPT;
  protected readonly maxUploadMb = MAX_UPLOAD_MB;
  protected readonly fmtDate = formatDate;
  protected readonly size = formatSize;

  protected readonly app = signal<Application | null>(null);
  protected readonly lines = signal<SupplyLine[]>([]);
  protected readonly municipalities = signal<Municipality[]>([]);
  protected readonly loadError = signal('');
  protected readonly saving = signal(false);
  protected readonly submitting = signal(false);
  protected readonly uploading = signal<string | null>(null);
  protected readonly actionError = signal('');
  /** Depois da 1ª tentativa de envio, as pendências aparecem nos campos. */
  protected readonly showIssues = signal(false);
  protected readonly justSubmitted = signal(false);

  protected readonly form = new FormGroup({
    supplierType: new FormControl<SupplierType | null>(null),
    legalName: new FormControl<string | null>(null),
    tradeName: new FormControl<string | null>(null),
    documentNumber: new FormControl<string | null>(null),
    birthDate: new FormControl<string | null>(null),
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
    lineCodes: new FormControl<string[]>([], { nonNullable: true }),
    linesDescription: new FormControl<string | null>(null),
    territories: new FormControl<Territory[]>([], { nonNullable: true }),
    territoriesNote: new FormControl<string | null>(null),
    bankName: new FormControl<string | null>(null),
    bankBranch: new FormControl<string | null>(null),
    bankAccount: new FormControl<string | null>(null),
    bankAccountType: new FormControl<BankAccountType | null>(null),
    pixKey: new FormControl<string | null>(null),
    accountHolder: new FormControl<string | null>(null),
    womanLed: new FormControl(false, { nonNullable: true }),
    publishWomanLed: new FormControl(false, { nonNullable: true }),
    race: new FormControl<Race | null>(null),
    cadUnico: new FormControl(false, { nonNullable: true }),
    nis: new FormControl<string | null>(null),
    declarationsAccepted: new FormControl(false, { nonNullable: true }),
  });

  /** Tipo atualmente marcado no formulário (pode ainda não estar salvo). */
  protected readonly type = signal<SupplierType | null>(null);
  protected readonly isIndividual = computed(() => this.type() === 'INDIVIDUAL');
  protected readonly selectedLines = signal<string[]>([]);
  protected readonly selectedTerritories = signal<Territory[]>([]);
  protected readonly cadUnico = signal(false);
  protected readonly dirty = signal(false);

  protected readonly issues = computed<Issue[]>(() => this.app()?.pendingItems ?? []);

  protected readonly sectionIssues = computed(() => {
    const map = new Map<string, Issue[]>();
    for (const s of SECTIONS) {
      map.set(
        s.id,
        this.issues().filter((i) => s.fields.some((f) => i.field === f || i.field.startsWith(f))),
      );
    }
    // Sem tipo de negócio ainda não há lista de documentos: a etapa não está completa.
    if (!this.app()?.supplierType) {
      map.set('documentos', [
        { field: 'documents.', message: 'Escolha o tipo de negócio para ver os documentos.' },
      ]);
    }
    return map;
  });

  protected readonly checklist = computed<ChecklistItem[]>(() => this.app()?.checklist ?? []);
  protected readonly requiredDone = computed(() => {
    const required = this.checklist().filter((c) => c.requirement === 'REQUIRED');
    return { done: required.filter((c) => c.document).length, total: required.length };
  });

  /** Recurso contra a decisão atual (ignora recurso de uma inabilitação anterior). */
  protected readonly currentAppeal = computed(() => {
    const a = this.app();
    const ap = a?.appeal;
    if (!a || !ap) {
      return null;
    }
    if (ap.status === 'GRANTED') {
      return a.status === 'APPROVED' ? ap : null;
    }
    return a.status === 'REJECTED' &&
      a.decidedAt &&
      new Date(ap.filedAt).getTime() >= new Date(a.decidedAt).getTime()
      ? ap
      : null;
  });

  protected readonly firstName = computed(() => this.auth.user()?.fullName.split(' ')[0] ?? '');

  ngOnInit(): void {
    forkJoin({
      app: this.api.get(),
      lines: this.catalog.supplyLines(),
      municipalities: this.catalog.municipalities(),
    }).subscribe({
      next: ({ app, lines, municipalities }) => {
        this.lines.set(lines.filter((l) => l.active));
        this.municipalities.set(municipalities);
        this.load(app);
      },
      error: (e: unknown) => this.loadError.set(problemMessage(e)),
    });

    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((v) => {
      this.type.set(v.supplierType ?? null);
      this.selectedLines.set(v.lineCodes ?? []);
      this.selectedTerritories.set(v.territories ?? []);
      this.cadUnico.set(!!v.cadUnico);
      this.dirty.set(this.form.dirty);
    });
    // Trocar o tipo muda a lista de documentos: salva na hora para o checklist acompanhar.
    this.form.controls.supplierType.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.app()?.editable) {
          this.save(true);
        }
      });
  }

  private load(app: Application): void {
    this.app.set(app);
    this.form.patchValue(
      {
        supplierType: app.supplierType,
        legalName: app.legalName,
        tradeName: app.tradeName,
        documentNumber: app.documentNumber ? formatDocument(app.documentNumber) : null,
        birthDate: app.birthDate,
        street: app.street,
        addressNumber: app.addressNumber,
        addressComplement: app.addressComplement,
        district: app.district,
        municipalityId: app.municipalityId,
        cep: app.cep ? formatCep(app.cep) : null,
        phone: app.phone ? formatPhone(app.phone) : null,
        contactEmail: app.contactEmail,
        website: app.website,
        representativeName: app.representativeName,
        representativeCpf: app.representativeCpf ? formatCpf(app.representativeCpf) : null,
        representativeRole: app.representativeRole,
        lineCodes: app.lines.map((l) => l.code),
        linesDescription: app.linesDescription,
        territories: app.territories,
        territoriesNote: app.territoriesNote,
        bankName: app.bankName,
        bankBranch: app.bankBranch,
        bankAccount: app.bankAccount,
        bankAccountType: app.bankAccountType,
        pixKey: app.pixKey,
        accountHolder: app.accountHolder,
        womanLed: app.womanLed,
        publishWomanLed: app.publishWomanLed,
        race: app.race,
        cadUnico: app.cadUnico,
        nis: app.nis,
        declarationsAccepted: app.declarationsAccepted,
      },
      { emitEvent: false },
    );
    this.type.set(app.supplierType);
    this.selectedLines.set(app.lines.map((l) => l.code));
    this.selectedTerritories.set(app.territories);
    this.cadUnico.set(app.cadUnico);
    this.form.markAsPristine();
    this.dirty.set(false);
    if (app.editable) {
      this.form.enable({ emitEvent: false });
    } else {
      this.form.disable({ emitEvent: false });
    }
    this.applyIssues();
  }

  private toForm(): ApplicationForm {
    const v = this.form.getRawValue();
    const text = (s: string | null) => (s && s.trim() ? s.trim() : null);
    const nums = (s: string | null) => (digits(s) ? digits(s) : null);
    return {
      ...v,
      legalName: text(v.legalName),
      tradeName: text(v.tradeName),
      documentNumber: nums(v.documentNumber),
      birthDate: v.birthDate || null,
      street: text(v.street),
      addressNumber: text(v.addressNumber),
      addressComplement: text(v.addressComplement),
      district: text(v.district),
      cep: nums(v.cep),
      phone: nums(v.phone),
      contactEmail: text(v.contactEmail),
      website: text(v.website),
      representativeName: text(v.representativeName),
      representativeCpf: nums(v.representativeCpf),
      representativeRole: text(v.representativeRole),
      linesDescription: text(v.linesDescription),
      territoriesNote: text(v.territoriesNote),
      bankName: text(v.bankName),
      bankBranch: text(v.bankBranch),
      bankAccount: text(v.bankAccount),
      pixKey: text(v.pixKey),
      accountHolder: text(v.accountHolder),
      nis: v.cadUnico ? nums(v.nis) : null,
      publishWomanLed: v.womanLed && v.publishWomanLed,
    };
  }

  private persist(): Observable<Application> {
    const app = this.app();
    if (!app) {
      return EMPTY;
    }
    return this.form.dirty ? this.api.save(this.toForm(), app.version) : of(app);
  }

  protected save(silent = false): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.actionError.set('');
    this.persist().subscribe({
      next: (app) => {
        this.load(app);
        this.saving.set(false);
        if (!silent) {
          this.snack.open('Rascunho salvo.', 'OK', { duration: 3000 });
        }
      },
      error: (e: unknown) => {
        this.saving.set(false);
        this.actionError.set(problemMessage(e));
      },
    });
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set('');
    this.showIssues.set(true);
    this.persist()
      .pipe(
        switchMap((saved) => {
          this.load(saved);
          return this.api.submit(saved.version);
        }),
      )
      .subscribe({
        next: (app) => {
          this.submitting.set(false);
          this.justSubmitted.set(true);
          this.load(app);
          window.scrollTo({ top: 0, behavior: 'smooth' });
        },
        error: (e: unknown) => {
          this.submitting.set(false);
          const problem = toProblem(e);
          if (problem.code === 'APPLICATION_INCOMPLETE') {
            // O backend devolve as pendências; atualiza a lista e leva ao resumo.
            this.api.get().subscribe((fresh) => {
              this.load(fresh);
              document.getElementById('pendencias')?.focus();
            });
          }
          this.actionError.set(problem.detail ?? problemMessage(e));
        },
      });
  }

  /** Mostra as pendências do servidor nos campos (depois da 1ª tentativa de envio). */
  private applyIssues(): void {
    for (const name of Object.keys(this.form.controls)) {
      const c = this.form.get(name);
      if (c?.hasError('server')) {
        c.setErrors(null);
      }
    }
    if (!this.showIssues()) {
      return;
    }
    for (const issue of this.issues()) {
      const c = this.form.get(issue.field);
      if (c && c.enabled) {
        c.setErrors({ server: issue.message });
        c.markAsTouched();
      }
    }
  }

  protected err(field: string): string | null {
    return (this.form.get(field)?.getError('server') as string | undefined) ?? null;
  }

  protected issueFor(field: string): string | null {
    return this.showIssues()
      ? (this.issues().find((i) => i.field === field)?.message ?? null)
      : null;
  }

  protected goTo(id: string): void {
    const el = document.getElementById('sec-' + id);
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    el?.focus({ preventScroll: true });
  }

  protected sectionFor(field: string): string {
    return (
      SECTIONS.find((s) => s.fields.some((f) => field === f || field.startsWith(f)))?.id ?? 'tipo'
    );
  }

  // --- Linhas e territórios ------------------------------------------------

  protected lineBlocked(line: SupplyLine): boolean {
    return this.isIndividual() && !line.allowsIndividual;
  }

  protected toggleLine(code: string, checked: boolean): void {
    const c = this.form.controls.lineCodes;
    const set = new Set(c.value);
    if (checked) {
      set.add(code);
    } else {
      set.delete(code);
    }
    c.setValue([...set].sort());
    c.markAsDirty();
  }

  protected toggleTerritory(t: Territory, checked: boolean): void {
    const c = this.form.controls.territories;
    const set = new Set(c.value);
    if (checked) {
      set.add(t);
    } else {
      set.delete(t);
    }
    c.setValue(TERRITORIES.map((x) => x.value).filter((x) => set.has(x)));
    c.markAsDirty();
  }

  // --- Máscaras (ao sair do campo) -----------------------------------------

  protected mask(field: 'documentNumber' | 'cep' | 'phone' | 'representativeCpf'): void {
    const c = this.form.controls[field];
    const v = c.value;
    if (!v) {
      return;
    }
    const formatted =
      field === 'documentNumber'
        ? this.isIndividual()
          ? formatCpf(v)
          : formatDocument(v)
        : field === 'cep'
          ? formatCep(v)
          : field === 'phone'
            ? formatPhone(v)
            : formatCpf(v);
    if (formatted !== v) {
      c.setValue(formatted);
    }
  }

  // --- Documentos ----------------------------------------------------------

  protected upload(item: ChecklistItem, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (file.size > MAX_UPLOAD_MB * 1024 * 1024) {
      this.snack.open(`O arquivo tem mais de ${MAX_UPLOAD_MB} MB.`, 'OK', { duration: 5000 });
      return;
    }
    this.uploading.set(item.type);
    this.persist()
      .pipe(
        switchMap(() => this.api.upload(item.type, file)),
        switchMap(() => this.api.get()),
      )
      .subscribe({
        next: (app) => {
          this.uploading.set(null);
          this.load(app);
          this.snack.open('Documento enviado.', 'OK', { duration: 3000 });
        },
        error: (e: unknown) => {
          this.uploading.set(null);
          this.snack.open(problemMessage(e), 'OK', { duration: 6000 });
        },
      });
  }

  protected removeDocument(item: ChecklistItem): void {
    if (!item.document) {
      return;
    }
    this.uploading.set(item.type);
    this.api
      .deleteDocument(item.document.id)
      .pipe(switchMap(() => this.api.get()))
      .subscribe({
        next: (app) => {
          this.uploading.set(null);
          this.load(app);
        },
        error: (e: unknown) => {
          this.uploading.set(null);
          this.snack.open(problemMessage(e), 'OK', { duration: 6000 });
        },
      });
  }

  protected openDocument(id: number): void {
    this.api.openDocument(id);
  }

  // --- Recurso (Edital 5.2; Anexo X) ------------------------------------------

  protected readonly appealOpen = signal(false);
  protected readonly appealBusy = signal(false);
  protected readonly appealError = signal('');
  protected readonly appealFile = signal<File | null>(null);
  protected readonly appealForm = new FormGroup({
    reasons: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(5000)],
    }),
    request: new FormControl('Reconsiderar a decisão e habilitar o meu credenciamento.', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(2000)],
    }),
  });

  protected pickAppealFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (file && file.size > MAX_UPLOAD_MB * 1024 * 1024) {
      input.value = '';
      this.snack.open(`O arquivo tem mais de ${MAX_UPLOAD_MB} MB.`, 'OK', { duration: 5000 });
      return;
    }
    this.appealFile.set(file);
  }

  protected sendAppeal(): void {
    if (this.appealForm.invalid) {
      this.appealForm.markAllAsTouched();
      return;
    }
    const { reasons, request } = this.appealForm.getRawValue();
    this.appealBusy.set(true);
    this.appealError.set('');
    this.api.appeal(reasons.trim(), request.trim(), this.appealFile()).subscribe({
      next: (app) => {
        this.appealBusy.set(false);
        this.appealOpen.set(false);
        this.load(app);
        this.snack.open('Recurso enviado à Comissão.', 'OK', { duration: 5000 });
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: (e: unknown) => {
        this.appealBusy.set(false);
        this.appealError.set(problemMessage(e));
      },
    });
  }

  protected openAppealAttachment(): void {
    this.api.openAppealAttachment();
  }
}
