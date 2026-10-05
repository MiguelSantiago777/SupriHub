import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

interface PublicDocument {
  title: string;
  description: string;
  file: string;
}

@Component({
  selector: 'app-documents-page',
  imports: [MatIconModule],
  template: `
    <header class="page-header">
      <div class="container">
        <span class="eyebrow">Documentos públicos</span>
        <h1>Edital e anexos</h1>
        <p>
          Documentos públicos do Contrata + NATA — Instituto NATA (Política art. 27). Arquivos no
          formato Word (.docx).
        </p>
      </div>
    </header>
    <div class="container">
      <ul class="docs">
        @for (doc of documents; track doc.file) {
          <li class="card card-hover">
            <span class="icon-tile"><mat-icon aria-hidden="true">description</mat-icon></span>
            <div>
              <h2>{{ doc.title }}</h2>
              <p>{{ doc.description }}</p>
              <a [href]="'documentos/' + doc.file" download>
                Baixar {{ doc.title }} <span class="visually-hidden">(arquivo .docx)</span>
              </a>
            </div>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .docs {
      list-style: none;
      padding: 0;
      display: grid;
      gap: 1rem;
    }
    .docs li {
      display: flex;
      gap: 1rem;
      align-items: flex-start;
    }
    .docs h2 {
      margin-top: 0;
      font-size: 1.15rem;
    }
    mat-icon {
      color: var(--sh-primary);
      flex: 0 0 auto;
    }
    a {
      font-weight: 700;
    }
  `,
})
export class DocumentsPage {
  protected readonly documents: PublicDocument[] = [
    {
      title: 'Edital de Credenciamento nº 001/2026',
      description:
        'Regras do credenciamento, habilitação, oportunidades, tratamento favorecido e pagamento.',
      file: 'edital-padrao-001-2026.docx',
    },
    {
      title: 'Anexos do Edital (formulários)',
      description:
        'Catálogo de linhas (Anexo I), requerimento, declarações, termo de adesão, DFD, modelo de proposta, ordem de fornecimento, recurso e demais anexos.',
      file: 'edital-padrao-001-2026-anexos-v2.docx',
    },
    {
      title: 'Política de Credenciamento e Contratação',
      description: 'Regulamento interno do Contrata + NATA aprovado pela Diretoria.',
      file: 'politica-credenciamento-contrata-mais-nata.docx',
    },
    {
      title: 'Manual Operacional',
      description: 'Passo a passo para fornecedores e equipe, prazos e perguntas frequentes.',
      file: 'manual-operacional-contrata-mais-nata.docx',
    },
  ];
}
