/**
 * Копирует текст в буфер обмена и сообщает, получилось ли. `navigator.clipboard` есть только в защищённом
 * контексте (HTTPS или localhost); по HTTP с адреса в сети (`http://192.168.x.x:8088`) — старый способ:
 * выделить скрытое поле и `execCommand('copy')`.
 */
export async function copyText(text: string): Promise<boolean> {
  if ('clipboard' in navigator) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // нет разрешения — пробуем старый способ
    }
  }
  const field = document.createElement('textarea');
  field.value = text;
  field.readOnly = true;
  field.style.position = 'fixed';
  field.style.opacity = '0';
  document.body.appendChild(field);
  field.select();
  try {
    return document.execCommand('copy');
  } catch {
    return false;
  } finally {
    field.remove();
  }
}
