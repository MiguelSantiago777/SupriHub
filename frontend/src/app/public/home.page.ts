import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

interface Item {
  icon: string;
  title: string;
  text: string;
}

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './home.page.html',
  styleUrl: './home.page.scss',
})
export class HomePage {
  protected readonly auth = inject(AuthService);

  protected readonly priorities: Item[] = [
    { icon: 'storefront', title: 'MEI', text: 'Microempreendedores individuais' },
    { icon: 'business', title: 'ME e EPP', text: 'Micro e pequenas empresas' },
    { icon: 'groups', title: 'Cooperativas', text: 'Inclusive de pessoas em vulnerabilidade' },
    { icon: 'person', title: 'Autônomos', text: 'Pessoa física, sem CNPJ, em várias linhas' },
    { icon: 'female', title: 'Negócios de mulheres', text: 'Preferência no desempate' },
    { icon: 'location_on', title: 'Território', text: 'Mesquita, Baixada e Região Metropolitana' },
  ];

  protected readonly steps: Item[] = [
    {
      icon: 'menu_book',
      title: 'Conheça as regras',
      text: 'Leia o Edital e veja no Catálogo as linhas de fornecimento: alimentação, eventos, transporte, oficinas e mais.',
    },
    {
      icon: 'how_to_reg',
      title: 'Faça seu cadastro',
      text: 'Crie sua conta, preencha o requerimento e envie os documentos (PDF ou foto). Dá para salvar e continuar depois.',
    },
    {
      icon: 'campaign',
      title: 'Receba oportunidades',
      text: 'Credenciado, você recebe as oportunidades da sua linha por e-mail e na sua área, e envia propostas.',
    },
  ];

  protected readonly lines: Item[] = [
    { icon: 'restaurant', title: 'Alimentação', text: 'Lanches, kits e bufê' },
    { icon: 'music_note', title: 'Som e música', text: 'Sonorização e apresentações' },
    { icon: 'directions_bus', title: 'Transporte', text: 'Pessoas e cargas' },
    { icon: 'palette', title: 'Oficinas e arte', text: 'Recreação e cultura' },
    { icon: 'checkroom', title: 'Confecção', text: 'Uniformes e camisetas' },
    { icon: 'build', title: 'Pequenos reparos', text: 'Manutenção em geral' },
  ];
}
