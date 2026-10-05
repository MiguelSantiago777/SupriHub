import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type AuthAside = 'login' | 'register' | 'default';

const ASIDES: Record<AuthAside, { title: string; items: { icon: string; text: string }[] }> = {
  login: {
    title: 'Que bom ver você de novo',
    items: [
      { icon: 'assignment_turned_in', text: 'Acompanhe seu requerimento e os prazos' },
      { icon: 'campaign', text: 'Veja as oportunidades da sua linha' },
      { icon: 'notifications_active', text: 'Receba avisos de certidões a vencer' },
    ],
  },
  register: {
    title: 'Comece seu credenciamento',
    items: [
      { icon: 'payments', text: 'Gratuito e sem intermediários' },
      { icon: 'save', text: 'Salve e continue o requerimento quando quiser' },
      { icon: 'event_available', text: 'Análise em até 10 dias úteis; vale por 24 meses' },
    ],
  },
  default: {
    title: 'Contrata + NATA',
    items: [
      { icon: 'lock', text: 'Seus dados protegidos conforme a LGPD' },
      { icon: 'support_agent', text: 'Dúvidas? Fale com a Comissão de Credenciamento' },
    ],
  },
};

/** Layout em duas colunas para as telas de acesso (painel da marca + formulário). */
@Component({
  selector: 'app-auth-layout',
  imports: [MatIconModule],
  template: `
    <div class="wrap">
      <div class="panel">
        <aside class="brand-panel" aria-hidden="true">
          <span class="mark">S</span>
          <h2>{{ aside().title }}</h2>
          <ul>
            @for (item of aside().items; track item.text) {
              <li>
                <mat-icon>{{ item.icon }}</mat-icon>
                <span>{{ item.text }}</span>
              </li>
            }
          </ul>
          <p class="foot">SupriHub · Contrata + NATA — Instituto NATA</p>
        </aside>
        <section class="form-side">
          <ng-content />
        </section>
      </div>
    </div>
  `,
  styles: `
    .wrap {
      max-width: 1040px;
      margin: 0 auto;
      padding: 1.25rem 1rem 0;
    }
    @media (min-width: 768px) {
      .wrap {
        padding: 3rem 1.5rem 0;
      }
    }
    .panel {
      display: grid;
      background: var(--sh-surface);
      border: 1px solid var(--sh-border);
      border-radius: var(--sh-radius-lg);
      box-shadow: var(--sh-shadow-lg);
      overflow: hidden;
    }
    @media (min-width: 880px) {
      .panel {
        grid-template-columns: 0.9fr 1.1fr;
      }
    }
    .brand-panel {
      display: none;
      flex-direction: column;
      gap: 1rem;
      padding: 2.5rem;
      color: #fff;
      background:
        radial-gradient(500px 300px at 100% 0%, rgba(255, 255, 255, 0.14), transparent 60%),
        var(--sh-gradient-deep);
    }
    @media (min-width: 880px) {
      .brand-panel {
        display: flex;
      }
    }
    .mark {
      display: grid;
      place-items: center;
      width: 52px;
      height: 52px;
      border-radius: 14px;
      background: rgba(255, 255, 255, 0.18);
      font-family: var(--sh-font-heading);
      font-weight: 800;
      font-size: 1.6rem;
    }
    .brand-panel h2 {
      color: #fff;
      font-size: 1.7rem;
      margin: 0.5rem 0;
    }
    .brand-panel ul {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      gap: 1rem;
    }
    .brand-panel li {
      display: flex;
      gap: 0.75rem;
      align-items: center;
    }
    .brand-panel mat-icon {
      flex: 0 0 auto;
      width: 40px;
      height: 40px;
      display: grid;
      place-items: center;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.16);
    }
    .foot {
      margin-top: auto;
      font-size: 0.85rem;
      opacity: 0.9;
    }
    .form-side {
      padding: 1.75rem 1.25rem;
    }
    @media (min-width: 768px) {
      .form-side {
        padding: 2.75rem 3rem;
      }
    }
  `,
})
export class AuthLayout {
  readonly variant = input<AuthAside>('default');
  protected readonly aside = computed(() => ASIDES[this.variant()]);
}
