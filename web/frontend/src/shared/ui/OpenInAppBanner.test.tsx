import { MantineProvider } from '@mantine/core';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { OpenInAppBanner } from './OpenInAppBanner';
import { ANDROID_PACKAGE, RUSTORE_URL, intentUrl } from './openInApp';

function renderBanner() {
  return render(
    <MantineProvider env="test">
      <MemoryRouter initialEntries={['/campaigns/12']}>
        <OpenInAppBanner />
      </MemoryRouter>
    </MantineProvider>,
  );
}

/** Android; `installed` — ответ getInstalledRelatedApps, `undefined` — браузер не умеет проверять. */
function mockAndroid(installed: { platform: string; id: string }[] | undefined) {
  vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Linux; Android 14) Chrome/130');
  Object.defineProperty(navigator, 'getInstalledRelatedApps', {
    configurable: true,
    value: installed === undefined ? undefined : () => Promise.resolve(installed),
  });
}

describe('Баннер «Продолжить в приложении»', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    sessionStorage.clear();
    localStorage.clear();
    Object.defineProperty(navigator, 'getInstalledRelatedApps', { configurable: true, value: undefined });
  });

  it('на Android с установленным приложением предлагает продолжить в нём', async () => {
    // подготовка
    mockAndroid([{ platform: 'play', id: ANDROID_PACKAGE }]);

    // вызов
    renderBanner();

    // проверка
    const button = await screen.findByRole('link', { name: 'Продолжить в приложении' });
    expect(screen.getByText(/установлено приложение «Companion PA»/)).toBeInTheDocument();
    expect(button.getAttribute('href')).toContain('intent://');
    expect(button.getAttribute('href')).toContain('/campaigns/12');
    expect(button.getAttribute('href')).toContain(`package=${ANDROID_PACKAGE}`);
    expect(button.getAttribute('href')).not.toContain(encodeURIComponent(RUSTORE_URL));
  });

  it.each([
    ['приложение не найдено (нет или версия до 1.3)', []],
    ['браузер не умеет проверять установленные приложения', undefined],
  ])('%s — та же кнопка, без приложения откроется RuStore', async (_case, installed) => {
    // подготовка
    mockAndroid(installed);

    // вызов
    renderBanner();

    // проверка
    const button = await screen.findByRole('link', { name: 'Продолжить в приложении' });
    expect(screen.getByText(/откроется RuStore/)).toBeInTheDocument();
    expect(button.getAttribute('href')).toContain('intent://');
    expect(button.getAttribute('href')).toContain('/campaigns/12');
    expect(button.getAttribute('href')).toContain(`S.browser_fallback_url=${encodeURIComponent(RUSTORE_URL)};`);
  });

  it('отказ от RuStore запоминается на 30 дней', async () => {
    // подготовка
    mockAndroid([]);
    const user = userEvent.setup();
    const first = renderBanner();
    await user.click(await screen.findByRole('button', { name: 'Остаться на сайте' }));
    first.unmount();

    // вызов: открыть сайт снова
    renderBanner();
    await Promise.resolve();

    // проверка
    expect(screen.queryByTestId('open-in-app-banner')).not.toBeInTheDocument();
    expect(Number(localStorage.getItem('primal.openInApp.storeDismissedAt'))).toBeGreaterThan(0);
  });

  it('через 30 дней после отказа предложение возвращается', async () => {
    // подготовка
    mockAndroid([]);
    localStorage.setItem('primal.openInApp.storeDismissedAt', String(Date.now() - 31 * 24 * 60 * 60 * 1000));

    // вызов
    renderBanner();

    // проверка
    expect(await screen.findByTestId('open-in-app-banner')).toBeInTheDocument();
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

  it('адрес intent:// по умолчанию возвращает в браузер, если приложения нет', () => {
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

  it('адрес intent:// с переходом в RuStore', () => {
    // вызов
    const url = intentUrl(
      { host: 'primalhelper.ru', pathname: '/', search: '', href: 'https://primalhelper.ru/' },
      RUSTORE_URL,
    );

    // проверка
    expect(url).toBe(
      'intent://primalhelper.ru/#Intent;scheme=https;package=com.primalapp.android;' +
        'S.browser_fallback_url=https%3A%2F%2Fwww.rustore.ru%2Fcatalog%2Fapp%2Fcom.primalapp.android;end',
    );
  });
});
