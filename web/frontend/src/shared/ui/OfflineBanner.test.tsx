import { MantineProvider } from '@mantine/core';
import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { OfflineBanner } from './OfflineBanner';

function setOnline(online: boolean) {
  vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(online);
  act(() => {
    window.dispatchEvent(new Event(online ? 'online' : 'offline'));
  });
}

describe('Баннер «Нет подключения»', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('появляется без сети и исчезает, когда сеть возвращается', () => {
    // подготовка
    render(
      <MantineProvider env="test">
        <OfflineBanner />
      </MantineProvider>,
    );
    expect(screen.queryByTestId('offline-banner')).not.toBeInTheDocument();

    // вызов
    setOnline(false);

    // проверка
    expect(screen.getByTestId('offline-banner')).toHaveTextContent('Нет подключения. Экспедиция и начатый бой работают');

    // вызов
    setOnline(true);

    // проверка
    expect(screen.queryByTestId('offline-banner')).not.toBeInTheDocument();
  });
});
