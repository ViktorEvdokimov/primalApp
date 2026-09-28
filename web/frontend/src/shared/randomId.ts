/**
 * Случайный UUID v4 — id боя в браузере. `crypto.randomUUID` есть только в защищённом контексте (HTTPS
 * или localhost), а сайт открывают и по адресу в локальной сети (`http://192.168.x.x:8088` с телефона) —
 * тогда UUID собирается из `crypto.getRandomValues`, доступного везде.
 */
export function randomId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  const hex = Array.from(bytes, (byte, index) => {
    // версия 4 в байте 6, вариант RFC 9562 в байте 8
    if (index === 6) return (byte & 0x0f) | 0x40;
    if (index === 8) return (byte & 0x3f) | 0x80;
    return byte;
  })
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
