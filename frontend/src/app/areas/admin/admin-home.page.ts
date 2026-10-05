import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-home-page',
  imports: [RouterLink, MatIconModule],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">ADMIN</span>
        <h1>Administração</h1>
        <p>Parâmetros de negócio, usuários e ferramentas técnicas.</p>
      </div>
    </header>
    <div class="container">
      <ul class="tiles">
        <li>
          <a routerLink="/comissao/requerimentos" class="card card-hover tile">
            <span class="icon-tile green"><mat-icon aria-hidden="true">how_to_reg</mat-icon></span>
            <span>
              <strong>Requerimentos de credenciamento</strong><br />
              Fila de análise: habilitar, pedir ajuste ou inabilitar fornecedores.
            </span>
          </a>
        </li>
        <li>
          <a routerLink="/comissao/editais" class="card card-hover tile">
            <span class="icon-tile"><mat-icon aria-hidden="true">campaign</mat-icon></span>
            <span>
              <strong>Editais</strong><br />
              Oportunidades de compra: publicar, rodízio e propostas recebidas.
            </span>
          </a>
        </li>
        <li>
          <a routerLink="/credenciados" class="card card-hover tile">
            <span class="icon-tile green"><mat-icon aria-hidden="true">storefront</mat-icon></span>
            <span>
              <strong>Credenciados</strong><br />
              Fornecedores habilitados por linha e território, com contato.
            </span>
          </a>
        </li>
        <li>
          <a routerLink="/recursos" class="card card-hover tile">
            <span class="icon-tile amber"><mat-icon aria-hidden="true">gavel</mat-icon></span>
            <span>
              <strong>Recursos</strong><br />
              Recursos contra inabilitação: reconsiderar, encaminhar e decidir.
            </span>
          </a>
        </li>
        <li>
          <a routerLink="parametros" class="card card-hover tile">
            <span class="icon-tile"><mat-icon aria-hidden="true">tune</mat-icon></span>
            <span>
              <strong>Parâmetros</strong><br />
              Limites das faixas, margens, prazos e metas — com histórico de alterações.
            </span>
          </a>
        </li>
        <li>
          <a routerLink="usuarios" class="card card-hover tile">
            <span class="icon-tile"><mat-icon aria-hidden="true">group</mat-icon></span>
            <span>
              <strong>Usuários</strong><br />
              Convidar equipe do Instituto, definir perfis e desativar acessos.
            </span>
          </a>
        </li>
        <li>
          <a href="/swagger-ui" class="card card-hover tile">
            <span class="icon-tile"><mat-icon aria-hidden="true">api</mat-icon></span>
            <span>
              <strong>Documentação da API</strong><br />
              Swagger UI (somente em ambiente de desenvolvimento).
            </span>
          </a>
        </li>
      </ul>
    </div>
  `,
  styles: `
    .tiles {
      list-style: none;
      padding: 0;
      display: grid;
      gap: 1rem;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    }
    .tile {
      display: flex;
      gap: 1rem;
      color: inherit;
      text-decoration: none;
      height: 100%;
      box-sizing: border-box;
    }
    .tile:hover {
      border-color: var(--sh-primary);
    }
    mat-icon {
      flex: 0 0 auto;
    }
  `,
})
export class AdminHomePage {}
