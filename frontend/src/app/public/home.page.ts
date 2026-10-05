import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <section class="hero">
      <div class="container">
        <h1>Venda para o Instituto NATA</h1>
        <p class="lead">
          O <strong>Contrata + NATA</strong> é o cadastro permanente e gratuito de fornecedores do
          Instituto NATA. Damos prioridade a
          <strong>MEIs, microempresas, cooperativas, profissionais autônomos</strong>,
          <strong>negócios liderados por mulheres</strong> e empreendedores de Mesquita e da Baixada
          Fluminense.
        </p>
        <div class="hero-actions">
          @if (auth.isAuthenticated()) {
            <a mat-flat-button [routerLink]="auth.homeRoute()">Ir para minha área</a>
          } @else {
            <a mat-flat-button routerLink="/criar-conta" class="hero-cta">Quero me credenciar</a>
            <a mat-stroked-button routerLink="/entrar" class="hero-secondary">Já tenho cadastro</a>
          }
        </div>
      </div>
    </section>

    <section class="container" aria-labelledby="como-funciona">
      <h2 id="como-funciona">Como funciona, em 3 passos</h2>
      <ol class="steps">
        <li class="card">
          <mat-icon aria-hidden="true">description</mat-icon>
          <h3>1. Conheça as regras</h3>
          <p>
            Leia o <a routerLink="/edital">Edital e os anexos</a> e veja no
            <a routerLink="/catalogo">Catálogo</a> as linhas de fornecimento: alimentação, eventos,
            transporte, oficinas, confecção, pequenos reparos e outras.
          </p>
        </li>
        <li class="card">
          <mat-icon aria-hidden="true">how_to_reg</mat-icon>
          <h3>2. Faça seu cadastro</h3>
          <p>
            Crie sua conta, preencha o requerimento pela internet e envie os documentos (PDF ou
            foto). Você pode salvar e continuar depois. A Comissão analisa em até
            <strong>10 dias úteis</strong>.
          </p>
        </li>
        <li class="card">
          <mat-icon aria-hidden="true">storefront</mat-icon>
          <h3>3. Receba oportunidades</h3>
          <p>
            Credenciado, você recebe as oportunidades da sua linha por e-mail e na sua área do site,
            envia propostas e acompanha os resultados. O credenciamento vale por
            <strong>24 meses</strong>.
          </p>
        </li>
      </ol>

      <div class="alert alert-info">
        <p>
          <strong>Não tem CNPJ?</strong> Profissionais autônomos podem se credenciar nas linhas que
          aceitam pessoa física (oficinas, apresentações, aulas, fotografia, pequenos reparos e
          outras). O credenciamento <strong>não tem custo</strong>.
        </p>
      </div>
    </section>
  `,
  styles: `
    .hero {
      background: var(--sh-surface);
      border-bottom: 1px solid #d0d7dc;
      padding: 1.5rem 0 2rem;
    }
    .lead {
      font-size: 1.15rem;
      max-width: 52rem;
    }
    .hero-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      margin-top: 1.25rem;
    }
    .hero-actions a {
      min-width: 240px;
    }
    .steps {
      list-style: none;
      padding: 0;
      display: grid;
      gap: 1rem;
      grid-template-columns: 1fr;
    }
    @media (min-width: 800px) {
      .steps {
        grid-template-columns: repeat(3, 1fr);
      }
    }
    .steps mat-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: var(--sh-primary);
    }
    .steps h3 {
      margin: 0.5rem 0;
    }
  `,
})
export class HomePage {
  protected readonly auth = inject(AuthService);
}
