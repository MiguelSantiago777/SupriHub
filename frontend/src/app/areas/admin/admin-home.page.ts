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
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
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
      color: var(--sh-primary);
      flex: 0 0 auto;
    }
  `,
})
export class AdminHomePage {}
