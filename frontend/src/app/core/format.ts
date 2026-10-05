/** Formatação de documentos e datas brasileiras para exibição (os dados vão ao backend só com dígitos). */

export function digits(value: string | null | undefined): string {
  return (value ?? '').replace(/\D/g, '');
}

export function formatCpf(value: string | null | undefined): string {
  const d = digits(value).slice(0, 11);
  return d
    .replace(/^(\d{3})(\d)/, '$1.$2')
    .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2');
}

export function formatCnpj(value: string | null | undefined): string {
  const d = digits(value).slice(0, 14);
  return d
    .replace(/^(\d{2})(\d)/, '$1.$2')
    .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
    .replace(/\.(\d{3})(\d)/, '.$1/$2')
    .replace(/(\d{4})(\d{1,2})$/, '$1-$2');
}

/** CPF (11 dígitos) ou CNPJ (14). */
export function formatDocument(value: string | null | undefined): string {
  return digits(value).length > 11 ? formatCnpj(value) : formatCpf(value);
}

export function formatCep(value: string | null | undefined): string {
  return digits(value)
    .slice(0, 8)
    .replace(/^(\d{5})(\d)/, '$1-$2');
}

export function formatPhone(value: string | null | undefined): string {
  const d = digits(value).slice(0, 11);
  if (d.length <= 2) {
    return d;
  }
  const ddd = `(${d.slice(0, 2)}) `;
  const rest = d.slice(2);
  if (rest.length <= 4) {
    return ddd + rest;
  }
  const split = rest.length === 9 ? 5 : 4;
  return `${ddd}${rest.slice(0, split)}-${rest.slice(split)}`;
}

/** Data ISO (yyyy-mm-dd) → dd/mm/aaaa, sem conversão de fuso. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  const [y, m, d] = iso.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function formatMoney(value: number | null | undefined): string {
  return value == null ? '' : BRL.format(value);
}

export function formatQuantity(value: number | null | undefined): string {
  return value == null ? '' : value.toLocaleString('pt-BR', { maximumFractionDigits: 2 });
}
