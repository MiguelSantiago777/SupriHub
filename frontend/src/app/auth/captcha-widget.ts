import { HttpClient } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  ElementRef,
  inject,
  Injectable,
  signal,
  viewChild,
  afterNextRender,
} from '@angular/core';
import { catchError, map, Observable, of, shareReplay } from 'rxjs';

interface Turnstile {
  render(el: HTMLElement, options: Record<string, unknown>): string;
  reset(widgetId: string): void;
  remove(widgetId: string): void;
}

declare global {
  interface Window {
    turnstile?: Turnstile;
  }
}

const SCRIPT_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';

/** Chave pública do captcha (Cloudflare Turnstile); null quando o backend não tem captcha. */
@Injectable({ providedIn: 'root' })
export class CaptchaConfig {
  private readonly http = inject(HttpClient);
  private script?: Promise<Turnstile>;

  readonly siteKey$: Observable<string | null> = this.http
    .get<{ captchaSiteKey?: string | null }>('/api/public/auth-config')
    .pipe(
      map((c) => c.captchaSiteKey ?? null),
      catchError(() => of(null)),
      shareReplay(1),
    );

  loadScript(): Promise<Turnstile> {
    this.script ??= new Promise((resolve, reject) => {
      if (window.turnstile) {
        resolve(window.turnstile);
        return;
      }
      const el = document.createElement('script');
      el.src = SCRIPT_URL;
      el.async = true;
      el.onload = () =>
        window.turnstile ? resolve(window.turnstile) : reject(new Error('turnstile'));
      el.onerror = () => {
        this.script = undefined;
        reject(new Error('turnstile'));
      };
      document.head.appendChild(el);
    });
    return this.script;
  }
}

/**
 * Verificação de segurança das telas públicas. Sem captcha configurado no backend, não
 * mostra nada e {@link valid} é sempre verdadeiro.
 */
@Component({
  selector: 'app-captcha',
  template: `
    <div #box class="captcha"></div>
    @if (failed()) {
      <p class="alert alert-error" role="alert">
        Não foi possível carregar a verificação de segurança. Recarregue a página e tente de novo.
      </p>
    }
  `,
  styles: `
    .captcha:empty {
      display: none;
    }
  `,
})
export class CaptchaWidget {
  private readonly config = inject(CaptchaConfig);
  private readonly box = viewChild.required<ElementRef<HTMLElement>>('box');

  private readonly enabled = signal(false);
  private readonly tokenValue = signal<string | null>(null);
  protected readonly failed = signal(false);
  private turnstile?: Turnstile;
  private widgetId?: string;

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const sub = this.config.siteKey$.subscribe((siteKey) => {
        if (siteKey) {
          this.enabled.set(true);
          void this.render(siteKey);
        }
      });
      destroyRef.onDestroy(() => {
        sub.unsubscribe();
        if (this.turnstile && this.widgetId) {
          this.turnstile.remove(this.widgetId);
        }
      });
    });
  }

  /** Pode enviar o formulário? */
  valid(): boolean {
    return !this.enabled() || this.tokenValue() !== null;
  }

  /** Token para o backend (undefined quando não há captcha). */
  token(): string | undefined {
    return this.tokenValue() ?? undefined;
  }

  /** O token é de uso único: depois de cada envio, pede um novo. */
  reset(): void {
    this.tokenValue.set(null);
    if (this.turnstile && this.widgetId) {
      this.turnstile.reset(this.widgetId);
    }
  }

  private async render(siteKey: string): Promise<void> {
    try {
      this.turnstile = await this.config.loadScript();
      this.widgetId = this.turnstile.render(this.box().nativeElement, {
        sitekey: siteKey,
        language: 'pt-br',
        callback: (t: string) => this.tokenValue.set(t),
        'expired-callback': () => this.tokenValue.set(null),
        'error-callback': () => this.tokenValue.set(null),
      });
    } catch {
      this.failed.set(true);
    }
  }
}
