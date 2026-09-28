import type { ReactNode } from 'react';
import type { RouteObject } from 'react-router';
import { LoginPage } from '../features/auth/LoginPage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { SettingsPage } from '../features/auth/SettingsPage';
import { BattlePage } from '../features/battle/BattlePage';
import { CampaignCreatePage } from '../features/campaign/CampaignCreatePage';
import { CampaignListPage } from '../features/campaign/CampaignListPage';
import { CampaignSheetPage } from '../features/campaign/CampaignSheetPage';
import { ExpeditionSetupPage } from '../features/expedition/ExpeditionSetupPage';
import { MainMenuPage } from '../features/menu/MainMenuPage';
import { CampaignBattleSetupPage } from '../features/progression/CampaignBattleSetupPage';
import { OutcomePage } from '../features/progression/OutcomePage';
import { TransitionPage } from '../features/progression/TransitionPage';
import { JoinPage } from '../features/sharing/JoinPage';
import { ru } from '../shared/i18n/ru';
import { isProtectedPath } from '../shared/routing';
import { PageStub } from '../shared/ui/PageStub';
import { Layout } from './Layout';

/** Экраны приложения (doc/architecture.md §5.1). Заглушки заменяются в задачах своих этапов. */
export const screens = [
  { path: '/expedition/new', title: ru.pages.expeditionNew, testId: 'page-expedition-new' },
  { path: '/battle', title: ru.pages.battle, testId: 'page-battle' },
  { path: '/login', title: ru.pages.login, testId: 'page-login' },
  { path: '/s/:token', title: ru.pages.join, testId: 'page-join' },
  { path: '/settings', title: ru.pages.settings, testId: 'page-settings' },
  { path: '/campaigns', title: ru.pages.campaigns, testId: 'page-campaigns' },
  { path: '/campaigns/new', title: ru.pages.campaignNew, testId: 'page-campaign-new' },
  { path: '/campaigns/:id', title: ru.pages.campaignSheet, testId: 'page-campaign-sheet' },
  { path: '/campaigns/:id/battle/new', title: ru.pages.campaignBattleNew, testId: 'page-campaign-battle-new' },
  { path: '/campaigns/:id/outcome', title: ru.pages.campaignOutcome, testId: 'page-campaign-outcome' },
  { path: '/campaigns/:id/transition', title: ru.pages.campaignTransition, testId: 'page-campaign-transition' },
] as const;

/** Готовые экраны; остальные пока показывают заглушку. */
const pages: Partial<Record<(typeof screens)[number]['path'], ReactNode>> = {
  '/expedition/new': <ExpeditionSetupPage />,
  '/battle': <BattlePage />,
  '/login': <LoginPage />,
  '/settings': <SettingsPage />,
  '/s/:token': <JoinPage />,
  '/campaigns': <CampaignListPage />,
  '/campaigns/new': <CampaignCreatePage />,
  '/campaigns/:id': <CampaignSheetPage />,
  '/campaigns/:id/battle/new': <CampaignBattleSetupPage />,
  '/campaigns/:id/outcome': <OutcomePage />,
  '/campaigns/:id/transition': <TransitionPage />,
};

export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { path: '/', element: <MainMenuPage /> },
      ...screens.map((screen) => {
        const page = pages[screen.path] ?? <PageStub title={screen.title} testId={screen.testId} />;
        // Кампании и настройки — после входа (doc/architecture.md §5.1)
        return { path: screen.path, element: isProtectedPath(screen.path) ? <RequireAuth>{page}</RequireAuth> : page };
      }),
      { path: '*', element: <PageStub title={ru.pages.notFound} testId="page-not-found" /> },
    ],
  },
];
