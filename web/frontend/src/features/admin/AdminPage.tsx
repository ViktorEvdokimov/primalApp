import { Alert, Anchor, Badge, Group, Loader, SegmentedControl, Select, Stack, Text, Title } from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import {
  useGetAdminCatalog,
  useResetChapterRewards,
  useResetQuestRewards,
  useUpdateChapterRewards,
  useUpdateQuestRewards,
} from '../../api/generated/admin/admin';
import { useAchievements, useBosses, useDictionaries } from '../../api/generated/catalog/catalog';
import type { AdminCatalog, AdminChapter, AdminQuest } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { errorMessage } from '../campaign/useCampaignSheet';
import { useCatalogCache } from './adminHooks';
import { Actions } from './adminShared';
import { BossEditor } from './BossEditor';
import { EffectList, type EditorOptions } from './EffectEditor';
import { InfoEditor } from './InfoEditor';
import { effectsFromJson, effectsToJson, problems, type EffectModel } from './effects';
import { ForgeEditor, LabEditor } from './PriceEditors';
import { StatsView } from './StatsView';

type Mode = 'STATS' | 'QUESTS' | 'CHAPTERS' | 'FORGE' | 'LAB' | 'BOSSES' | 'INFO';

const CATALOG_STALE_MS = 60 * 60 * 1000;

/**
 * Администрирование: статистика (qa № 140); правка каталога — награды заданий и глав (qa № 137), цены кузни и
 * лаборатории, характеристики монстров (qa № 138). Открывается только администратору ({@link RequireAdmin}); сервер тоже проверяет роль и остальным
 * отвечает 404.
 */
export default function AdminPage() {
  const catalog = useGetAdminCatalog({ query: { retry: false, staleTime: 0 } });
  const dictionaries = useDictionaries({ query: { staleTime: CATALOG_STALE_MS } });
  const achievements = useAchievements({ query: { staleTime: CATALOG_STALE_MS } });
  const bosses = useBosses({ query: { staleTime: CATALOG_STALE_MS } });
  const [mode, setMode] = useState<Mode>('STATS');
  // Пять разделов в строку на телефоне не помещаются
  const narrow = useMediaQuery('(max-width: 36em)') ?? false;
  const back = (
    <Anchor component={Link} to="/" size="sm" data-testid="admin-back">
      {ru.campaigns.toMenu}
    </Anchor>
  );

  const options = useMemo<EditorOptions | null>(() => {
    if (catalog.data === undefined || dictionaries.data === undefined || achievements.data === undefined || bosses.data === undefined) {
      return null;
    }
    return {
      resources: [...dictionaries.data.materials, ...dictionaries.data.plants].map((item) => ({ value: item.code, label: item.name })),
      quests: catalog.data.quests.map((quest) => ({ value: String(quest.number), label: ru.admin.questOption(quest.number, quest.name) })),
      achievements: achievements.data.map((item) => ({ value: item.code, label: item.name })),
      bosses: bosses.data.map((item) => ({ value: item.code, label: item.name })),
      expansions: Object.entries(ru.expansions).map(([value, label]) => ({ value, label })),
    };
  }, [catalog.data, dictionaries.data, achievements.data, bosses.data]);

  if (catalog.isError) {
    return (
      <Stack gap="md" data-testid="page-admin">
        {back}
        <Alert color="red">{ru.admin.loadError}</Alert>
      </Stack>
    );
  }
  if (catalog.data === undefined || options === null || dictionaries.data === undefined) {
    return (
      <Stack gap="md" data-testid="page-admin">
        {back}
        <Loader />
      </Stack>
    );
  }

  const modes = [
    { value: 'STATS', label: ru.admin.stats },
    { value: 'QUESTS', label: ru.admin.quests },
    { value: 'CHAPTERS', label: ru.admin.chapters },
    { value: 'FORGE', label: ru.admin.forge },
    { value: 'LAB', label: ru.admin.lab },
    { value: 'BOSSES', label: ru.admin.bosses },
    { value: 'INFO', label: ru.adminInfo.tab },
  ];

  return (
    <Stack gap="md" data-testid="page-admin">
      {back}
      <Title order={2}>{ru.admin.title}</Title>
      <SegmentedControl
        value={mode}
        onChange={(value) => setMode(value as Mode)}
        orientation={narrow ? 'vertical' : 'horizontal'}
        fullWidth
        data={modes}
        data-testid="admin-mode"
      />
      {mode === 'STATS' && <StatsView />}
      {mode === 'QUESTS' && <QuestsEditor catalog={catalog.data} options={options} />}
      {mode === 'CHAPTERS' && <ChaptersEditor catalog={catalog.data} options={options} />}
      {mode === 'FORGE' && <ForgeEditor catalog={catalog.data} dictionaries={dictionaries.data} />}
      {mode === 'LAB' && <LabEditor catalog={catalog.data} dictionaries={dictionaries.data} />}
      {mode === 'BOSSES' && <BossEditor catalog={catalog.data} />}
      {mode === 'INFO' && <InfoEditor />}
    </Stack>
  );
}

function QuestsEditor({ catalog, options }: { catalog: AdminCatalog; options: EditorOptions }) {
  const [number, setNumber] = useState(catalog.quests[0]?.number ?? 1);
  const quest = catalog.quests.find((item) => item.number === number);
  return (
    <Stack gap="md">
      <Select
        label={ru.admin.quest}
        searchable
        value={String(number)}
        data={catalog.quests.map((item) => ({
          value: String(item.number),
          label:
            ru.admin.questOption(item.number, item.name) +
            (item.expansion === null ? '' : ` · ${ru.expansions[item.expansion] ?? item.expansion}`) +
            (item.edited ? ` · ${ru.admin.edited}` : ''),
        }))}
        onChange={(value) => value !== null && setNumber(Number(value))}
        allowDeselect={false}
        data-testid="admin-quest"
      />
      {quest !== undefined && <QuestForm key={`${quest.number}-${String(quest.edited)}`} quest={quest} options={options} />}
    </Stack>
  );
}

function QuestForm({ quest, options }: { quest: AdminQuest; options: EditorOptions }) {
  const cache = useCatalogCache();
  const update = useUpdateQuestRewards();
  const reset = useResetQuestRewards();
  const [victory, setVictory] = useState<EffectModel[]>(() => effectsFromJson(quest.victory));
  const [expired, setExpired] = useState<EffectModel[]>(() => effectsFromJson(quest.expired));
  const [error, setError] = useState<string | null>(null);
  const missing = problems(victory) + problems(expired);
  const dirty =
    JSON.stringify(effectsToJson(victory)) !== JSON.stringify(effectsToJson(effectsFromJson(quest.victory))) ||
    JSON.stringify(effectsToJson(expired)) !== JSON.stringify(effectsToJson(effectsFromJson(quest.expired)));

  const save = () => {
    setError(null);
    update.mutateAsync({ number: quest.number, data: { victory: effectsToJson(victory), expired: effectsToJson(expired) } }).then(
      (saved) => {
        cache.quest(saved);
        notifications.show({ color: 'teal', message: ru.admin.saved });
      },
      (cause: unknown) => setError(errorMessage(cause)),
    );
  };
  const restore = () =>
    reset.mutateAsync({ number: quest.number }).then(
      (restored) => {
        cache.quest(restored);
        notifications.show({ color: 'teal', message: ru.admin.resetDone });
      },
      (cause: unknown) => setError(errorMessage(cause)),
    );

  return (
    <Stack gap="md" data-testid="admin-quest-form" data-number={quest.number}>
      <Group gap="xs">
        <Text c="dimmed" size="sm">
          {ru.admin.boss(quest.bossName)}
        </Text>
        {quest.expansion !== null && (
          <Badge variant="light" data-testid="admin-quest-expansion">
            {ru.expansions[quest.expansion] ?? quest.expansion}
          </Badge>
        )}
        {quest.edited && (
          <Badge color="orange" variant="light" data-testid="admin-edited">
            {ru.admin.edited}
          </Badge>
        )}
      </Group>
      <Section title={ru.admin.victory} text={quest.victoryText}>
        <EffectList effects={victory} onChange={setVictory} options={options} testId="admin-victory" />
      </Section>
      <Section title={ru.admin.expired} text={quest.expiredText}>
        <EffectList effects={expired} onChange={setExpired} options={options} testId="admin-expired" />
      </Section>
      <Actions
        error={error}
        missing={missing}
        dirty={dirty}
        edited={quest.edited}
        saving={update.isPending}
        onSave={save}
        onDiscard={() => {
          setVictory(effectsFromJson(quest.victory));
          setExpired(effectsFromJson(quest.expired));
          setError(null);
        }}
        onReset={restore}
      />
    </Stack>
  );
}

function ChaptersEditor({ catalog, options }: { catalog: AdminCatalog; options: EditorOptions }) {
  const [number, setNumber] = useState(catalog.chapters[0]?.chapter ?? 1);
  const chapter = catalog.chapters.find((item) => item.chapter === number);
  return (
    <Stack gap="md">
      <Select
        label={ru.admin.chapter}
        value={String(number)}
        data={catalog.chapters.map((item) => ({
          value: String(item.chapter),
          label: ru.admin.chapterOption(item.chapter) + (item.edited ? ` · ${ru.admin.edited}` : ''),
        }))}
        onChange={(value) => value !== null && setNumber(Number(value))}
        allowDeselect={false}
        data-testid="admin-chapter"
      />
      {chapter !== undefined && (
        <ChapterForm key={`${chapter.chapter}-${String(chapter.edited)}`} chapter={chapter} options={options} />
      )}
    </Stack>
  );
}

function ChapterForm({ chapter, options }: { chapter: AdminChapter; options: EditorOptions }) {
  const cache = useCatalogCache();
  const update = useUpdateChapterRewards();
  const reset = useResetChapterRewards();
  const [effects, setEffects] = useState<EffectModel[]>(() => effectsFromJson(chapter.effects));
  const [error, setError] = useState<string | null>(null);
  const dirty = JSON.stringify(effectsToJson(effects)) !== JSON.stringify(effectsToJson(effectsFromJson(chapter.effects)));

  const save = () => {
    setError(null);
    update.mutateAsync({ number: chapter.chapter, data: { effects: effectsToJson(effects) } }).then(
      (saved) => {
        cache.chapter(saved);
        notifications.show({ color: 'teal', message: ru.admin.saved });
      },
      (cause: unknown) => setError(errorMessage(cause)),
    );
  };
  const restore = () =>
    reset.mutateAsync({ number: chapter.chapter }).then(
      (restored) => {
        cache.chapter(restored);
        notifications.show({ color: 'teal', message: ru.admin.resetDone });
      },
      (cause: unknown) => setError(errorMessage(cause)),
    );

  return (
    <Stack gap="md" data-testid="admin-chapter-form" data-chapter={chapter.chapter}>
      {chapter.edited && (
        <Badge color="orange" variant="light" data-testid="admin-edited">
          {ru.admin.edited}
        </Badge>
      )}
      <Section title={ru.admin.chapterEffects} text={chapter.text}>
        <Text size="xs" c="dimmed">
          {ru.admin.chapterHint}
        </Text>
        <EffectList effects={effects} onChange={setEffects} options={options} testId="admin-chapter-effects" />
      </Section>
      <Actions
        error={error}
        missing={problems(effects)}
        dirty={dirty}
        edited={chapter.edited}
        saving={update.isPending}
        onSave={save}
        onDiscard={() => {
          setEffects(effectsFromJson(chapter.effects));
          setError(null);
        }}
        onReset={restore}
      />
    </Stack>
  );
}

function Section({ title, text, children }: { title: string; text: string[]; children: ReactNode }) {
  return (
    <Stack gap="xs">
      <Title order={4}>{title}</Title>
      {children}
      <Stack gap={2} data-testid="admin-preview">
        <Text size="xs" c="dimmed">
          {ru.admin.preview}
        </Text>
        {text.length === 0 ? (
          <Text size="sm" c="dimmed">
            {ru.admin.empty}
          </Text>
        ) : (
          text.map((line, index) => (
            <Text key={index} size="sm" data-testid="admin-preview-line">
              • {line}
            </Text>
          ))
        )}
      </Stack>
    </Stack>
  );
}
