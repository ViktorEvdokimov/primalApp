import { afterEach, describe, expect, it, vi } from 'vitest';
import { copyText } from './copyText';

/** jsdom не реализует execCommand — подменяем его. */
function stubExecCommand(result: boolean | Error) {
  const selected: string[] = [];
  const execCommand = vi.fn((command: string) => {
    const field = document.activeElement instanceof HTMLTextAreaElement ? document.activeElement : document.querySelector('textarea');
    selected.push(`${command}:${field?.value ?? ''}`);
    if (result instanceof Error) throw result;
    return result;
  });
  Object.defineProperty(document, 'execCommand', { value: execCommand, configurable: true });
  return { execCommand, selected };
}

describe('copyText', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(document, 'execCommand');
  });

  it('в защищённом контексте — navigator.clipboard', async () => {
    // подготовка
    const writeText = vi.fn(() => Promise.resolve());
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    const { execCommand } = stubExecCommand(true);

    // вызов
    const copied = await copyText('http://localhost:8088/s/abc');

    // проверка
    expect(copied).toBe(true);
    expect(writeText).toHaveBeenCalledWith('http://localhost:8088/s/abc');
    expect(execCommand).not.toHaveBeenCalled();
  });

  it('по HTTP с адреса в сети (нет navigator.clipboard) — через скрытое поле, поле убирается', async () => {
    // подготовка
    vi.stubGlobal('navigator', {});
    const { selected } = stubExecCommand(true);

    // вызов
    const copied = await copyText('http://192.168.0.31:8088/s/abc');

    // проверка
    expect(copied).toBe(true);
    expect(selected).toEqual(['copy:http://192.168.0.31:8088/s/abc']);
    expect(document.querySelector('textarea')).toBeNull();
  });

  it('буфер отказал — старый способ', async () => {
    // подготовка
    vi.stubGlobal('navigator', { clipboard: { writeText: () => Promise.reject(new Error('NotAllowedError')) } });
    const { selected } = stubExecCommand(true);

    // вызов
    const copied = await copyText('текст');

    // проверка
    expect(copied).toBe(true);
    expect(selected).toEqual(['copy:текст']);
  });

  it('скопировать не удалось никак — false', async () => {
    // подготовка
    vi.stubGlobal('navigator', {});
    stubExecCommand(false);

    // вызов и проверка
    expect(await copyText('текст')).toBe(false);

    // подготовка: execCommand бросает
    stubExecCommand(new Error('SecurityError'));

    // вызов и проверка
    expect(await copyText('текст')).toBe(false);
    expect(document.querySelector('textarea')).toBeNull();
  });
});
