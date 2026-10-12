import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { OpenInAppBanner } from './OpenInAppBanner';
import { ANDROID_PACKAGE, intentUrl } from './openInApp';

function renderBanner() {
  return render(
    <MantineProvider env="test">
      <MemoryRouter initialEntries={['/campaigns/12']}>
        <OpenInAppBanner />
      </MemoryRouter>
    </MantineProvider>,
  );
}

function mockAndroid(installed: { platform: string; id: string }[] | undefined) {
  vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Linux; Android 14) Chrome/130');
  Object.defineProperty(navigator, 'getInstalledRelatedApps', {
    configurable: true,
    value: installed === undefined ? undefined : () => Promise.resolve(installed),
  });
}

describe('Баннер «Открыть в приложении»', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
    Object.defineProperty(navigator, 'getInstalledRelatedApps', { configurable: true, value: undefined });
  });

  it('на Android с установленным приложением предлагает открыть страницу в нём', async () => {
    // подготовка
    mockAndroid([{ platform: 'play', id: ANDROID_PACKAGE }]);

    // вызов
    renderBanner();

    // проверка
    const button = await screen.findByRole('link', { name: 'Открыть в приложении' });
    expect(button.getAttribute('href')).toContain('intent://');
    expect(button.getAttribute('href')).toContain('/campaigns/12');
    expect(button.getAttribute('href')).toContain(`package=${ANDROID_PACKAGE}`);
  });

  it('без установленного приложения баннера нет', async () => {
    // подготовка
    mockAndroid([]);

    // вызов
    renderBanner();
    await Promise.resolve();

    // проверка
    expect(screen.queryByTestId('open-in-app-banner')).not.toBeInTheDocument();
  });

  it('не на Android баннера нет', async () => {
    // подготовка
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Windows NT 10.0) Chrome/130');
    Object.defineProperty(navigator, 'getInstalledRelatedApps', {
      configurable: true,
      value: () => Promise.resolve([{ platform: 'play', id: ANDROID_PACKAGE }]),
    });

    // вызов
    renderBanner();
    await Promise.resolve();

    // проверка
    expect(screen.queryByTestId('open-in-app-banner')).not.toBeInTheDocument();
  });

  it('адрес intent:// возвращает в браузер, если приложения нет', () => {
    // вызов
    const url = intentUrl({
      host: 'primalhelper.ru',
      pathname: '/s/abc',
      search: '',
      href: 'https://primalhelper.ru/s/abc',
    });

    // проверка
    expect(url).toBe(
      'intent://primalhelper.ru/s/abc#Intent;scheme=https;package=com.primalapp.android;' +
        'S.browser_fallback_url=https%3A%2F%2Fprimalhelper.ru%2Fs%2Fabc;end',
    );
  });
});
