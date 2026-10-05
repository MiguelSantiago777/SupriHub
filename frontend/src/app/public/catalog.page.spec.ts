import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SupplyLine } from '../core/models';
import { CatalogPage } from './catalog.page';

const line = (code: string, name: string, scope: string, pf: boolean): SupplyLine => ({
  code,
  name,
  shortName: name,
  scope,
  cnaeReference: '0000-0/00',
  allowsIndividual: pf,
  allowsMediumLarge: false,
  active: true,
  version: 0,
});

describe('CatalogPage', () => {
  it('lista as linhas e filtra por busca sem acento e por PF', async () => {
    TestBed.configureTestingModule({
      imports: [CatalogPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(CatalogPage);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/public/supply-lines')
      .flush([
        line('L01', 'Alimentação — lanches, coffee break e kits', 'Kits lanche', false),
        line('L03', 'Sonorização, iluminação e apresentações musicais', 'Som e DJ', true),
        line('L14', 'Transporte de pessoas e cargas', 'Vans e ônibus', false),
      ]);
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.componentInstance as unknown as {
      query: { set(v: string): void };
      onlyPf: { set(v: boolean): void };
      filtered(): SupplyLine[];
    };
    expect(page.filtered().map((l) => l.code)).toEqual(['L01', 'L03', 'L14']);

    page.query.set('alimentacao');
    expect(page.filtered().map((l) => l.code)).toEqual(['L01']);

    page.query.set('');
    page.onlyPf.set(true);
    expect(page.filtered().map((l) => l.code)).toEqual(['L03']);

    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      '1 linha(s) encontrada(s)',
    );
  });
});
