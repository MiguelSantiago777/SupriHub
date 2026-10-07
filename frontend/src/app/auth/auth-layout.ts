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

/**
 * Layout das telas de acesso: fundo com o degradê da marca de ponta a ponta e o formulário
 * num card de vidro fosco. Os campos translúcidos ficam em `.auth-glass` (styles.scss),
 * porque o conteúdo projetado não recebe os estilos encapsulados deste componente.
 */
@Component({
  selector: 'app-auth-layout',
  imports: [MatIconModule],
  template: `
    <div class="backdrop">
      <span class="orb orb-a" aria-hidden="true"></span>
      <span class="orb orb-b" aria-hidden="true"></span>
      <span class="orb orb-c" aria-hidden="true"></span>
      <div class="wrap">
        <aside class="brand-panel" aria-hidden="true">
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
        <section class="glass-card auth-glass">
          <ng-content />
        </section>
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: -3rem; // encosta no rodapé (main tem margem antes do footer)
    }
    .backdrop {
      position: relative;
      overflow: hidden;
      isolation: isolate;
      display: flex;
      align-items: center;
      min-height: calc(100vh - 80px);
      background:
        radial-gradient(900px 500px at 100% 0%, rgba(255, 255, 255, 0.12), transparent 60%),
        var(--sh-gradient-deep);
    }
    .orb {
      position: absolute;
      z-index: -1;
      border-radius: 50%;
      filter: blur(8px);
      pointer-events: none;
    }
    .orb-a {
      width: 420px;
      height: 420px;
      right: -80px;
      top: 8%;
      background: radial-gradient(circle at 30% 30%, #34b8f0, rgba(0, 144, 208, 0) 70%);
      opacity: 0.85;
    }
    .orb-b {
      width: 360px;
      height: 360px;
      right: 22%;
      bottom: -120px;
      background: radial-gradient(circle at 50% 50%, #8fd65a, rgba(124, 194, 66, 0) 70%);
      opacity: 0.7;
    }
    .orb-c {
      width: 260px;
      height: 260px;
      left: -60px;
      top: -40px;
      background: radial-gradient(circle, rgba(255, 255, 255, 0.35), transparent 70%);
    }
    .wrap {
      flex: 1;
      display: grid;
      gap: 2.5rem;
      align-items: center;
      max-width: 1100px;
      margin: 0 auto;
      padding: 1.5rem 1rem 3rem;
    }
    @media (min-width: 768px) {
      .wrap {
        padding: 3.5rem 1.5rem 4.5rem;
      }
    }
    @media (min-width: 880px) {
      .wrap {
        grid-template-columns: 0.9fr 1.1fr;
        gap: 4rem;
      }
    }
    .brand-panel {
      display: none;
      flex-direction: column;
      gap: 1rem;
      color: #fff;
    }
    @media (min-width: 880px) {
      .brand-panel {
        display: flex;
      }
    }
    .brand-panel h2 {
      color: #fff;
      font-size: 2rem;
      line-height: 1.2;
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
      gap: 0.85rem;
      align-items: center;
    }
    .brand-panel mat-icon {
      flex: 0 0 auto;
      width: 44px;
      height: 44px;
      display: grid;
      place-items: center;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.14);
      border: 1px solid rgba(255, 255, 255, 0.22);
    }
    .foot {
      margin: 1.5rem 0 0;
      font-size: 0.85rem;
      opacity: 0.85;
    }
    .glass-card {
      position: relative;
      padding: 1.75rem 1.25rem;
      border-radius: var(--sh-radius-lg);
      background: rgba(255, 255, 255, 0.7);
      border: 1px solid rgba(255, 255, 255, 0.65);
      box-shadow:
        0 30px 60px -20px rgba(6, 26, 46, 0.5),
        inset 0 1px 0 rgba(255, 255, 255, 0.8);
      -webkit-backdrop-filter: blur(22px) saturate(160%);
      backdrop-filter: blur(22px) saturate(160%);
    }
    @media (min-width: 768px) {
      .glass-card {
        padding: 2.75rem 3rem;
      }
    }
    // Sem suporte a backdrop-filter: card mais opaco para manter a leitura
    @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
      .glass-card {
        background: rgba(255, 255, 255, 0.94);
      }
    }
  `,
})
export class AuthLayout {
  readonly variant = input<AuthAside>('default');
  protected readonly aside = computed(() => ASIDES[this.variant()]);
}
