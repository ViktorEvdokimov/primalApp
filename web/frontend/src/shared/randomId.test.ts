import { afterEach, describe, expect, it, vi } from 'vitest';
import { randomId } from './randomId';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('randomId', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('в защищённом контексте — crypto.randomUUID', () => {
    // подготовка
    const randomUUID = vi.fn(() => '00000000-0000-4000-8000-000000000000' as const);
    vi.stubGlobal('crypto', { randomUUID, getRandomValues: crypto.getRandomValues.bind(crypto) });

    // вызов
    const id = randomId();

    // проверка
    expect(id).toBe('00000000-0000-4000-8000-000000000000');
    expect(randomUUID).toHaveBeenCalledOnce();
  });

  it('по HTTP с адреса в сети (нет crypto.randomUUID) — UUID v4 из getRandomValues', () => {
    // подготовка: так браузер ведёт себя на http://192.168.x.x
    const getRandomValues = crypto.getRandomValues.bind(crypto);
    vi.stubGlobal('crypto', { getRandomValues });

    // вызов
    const ids = Array.from({ length: 100 }, () => randomId());

    // проверка
    ids.forEach((id) => expect(id).toMatch(UUID_V4));
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('биты версии и варианта выставляются при любых случайных байтах', () => {
    // подготовка: все байты 0xff и 0x00
    vi.stubGlobal('crypto', { getRandomValues: (bytes: Uint8Array) => bytes.fill(0xff) });
    const ones = randomId();
    vi.stubGlobal('crypto', { getRandomValues: (bytes: Uint8Array) => bytes.fill(0x00) });
    const zeros = randomId();

    // проверка
    expect(ones).toBe('ffffffff-ffff-4fff-bfff-ffffffffffff');
    expect(zeros).toBe('00000000-0000-4000-8000-000000000000');
  });
});
