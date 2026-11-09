/** Converte una Date locale in `aaaa-mm-gg` (formato inviato al backend). */
export function toIsoDate(value: Date | null): string | null {
  if (!(value instanceof Date)) {
    return null;
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`;
}

/** Converte `aaaa-mm-gg` in `gg/mm/aaaa` per la visualizzazione. */
export function formatItalianDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${day}/${month}/${year}`;
}
