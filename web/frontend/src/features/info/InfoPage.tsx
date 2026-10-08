import {
  Accordion,
  Alert,
  Anchor,
  Badge,
  Card,
  CloseButton,
  Group,
  Image,
  Loader,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { useGetInfo } from '../../api/generated/info/info';
import type { InfoEntry, InfoSectionView } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { InfoText } from './InfoText';
import {
  CARD_SECTIONS,
  entryAnchor,
  entryHref,
  entryLabel,
  matches,
  resolver,
  SECTION_SLUGS,
  sectionBySlug,
} from './infoModel';

/** «Инфо» берётся при каждом открытии с проверкой по ETag; без сети — из кэша service worker. */
function useInfo() {
  return useGetInfo({ query: { staleTime: 60_000 } });
}

/** «Назад»: туда, откуда пришли (бой, лист кампании), а если открыли сразу — в главное меню. */
function BackLink() {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <Anchor
      component="button"
      type="button"
      size="sm"
      onClick={() => (location.key === 'default' ? void navigate('/') : void navigate(-1))}
      style={{ alignSelf: 'flex-start' }}
      data-testid="info-back"
    >
      {ru.info.back}
    </Anchor>
  );
}

function Frame({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Stack gap="md" data-testid="page-info">
      <BackLink />
      <Title order={2}>{title}</Title>
      {children}
    </Stack>
  );
}

function SearchField({
  value,
  onChange,
  testId,
}: {
  value: string;
  onChange: (value: string) => void;
  testId: string;
}) {
  return (
    <TextInput
      placeholder={ru.info.search}
      aria-label={ru.info.search}
      value={value}
      onChange={(event) => onChange(event.currentTarget.value)}
      rightSection={value === '' ? null : <CloseButton aria-label={ru.info.clear} onClick={() => onChange('')} />}
      data-testid={testId}
    />
  );
}

/**
 * «Инфо» (qa № 142): поиск по всем разделам и список разделов — «Ключевые слова», «Символы реакций монстров»,
 * «Жетоны окружения».
 */
export function InfoPage() {
  const info = useInfo();
  const [query, setQuery] = useState('');
  const all = useMemo(() => info.data?.sections.flatMap((section) => section.entries) ?? [], [info.data]);

  if (info.isError && info.data === undefined) {
    return (
      <Frame title={ru.info.title}>
        <Alert color="red">{ru.info.loadError}</Alert>
      </Frame>
    );
  }
  if (info.data === undefined) {
    return (
      <Frame title={ru.info.title}>
        <Loader />
      </Frame>
    );
  }
  const found = query.trim() === '' ? [] : all.filter((entry) => matches(entry, query));
  const titles = new Map(info.data.sections.map((section) => [section.code, section.title]));

  return (
    <Frame title={ru.info.title}>
      <SearchField value={query} onChange={setQuery} testId="info-search" />
      {query.trim() !== '' ? (
        <Stack gap="xs" data-testid="info-results">
          {found.length === 0 && (
            <Text c="dimmed" data-testid="info-nothing">
              {ru.info.nothing}
            </Text>
          )}
          {found.map((entry) => (
            <Anchor
              key={entry.id}
              component={Link}
              to={entryHref(entry)}
              data-testid="info-result"
              data-entry={entry.id}
            >
              {entryLabel(entry)}
              <Text span size="xs" c="dimmed">
                {' · '}
                {titles.get(entry.section)}
              </Text>
            </Anchor>
          ))}
        </Stack>
      ) : (
        <Stack gap="sm">
          {info.data.sections.map((section) => (
            <Card
              key={section.code}
              withBorder
              padding="md"
              component={Link}
              to={`/info/${SECTION_SLUGS[section.code]}`}
              data-testid="info-section"
              data-section={section.code}
            >
              <Group justify="space-between">
                <Text fw={600}>{section.title}</Text>
                {section.entries.length === 0 ? (
                  <Badge variant="light" color="gray">
                    {ru.info.soon}
                  </Badge>
                ) : (
                  <Badge variant="light">{section.entries.length}</Badge>
                )}
              </Group>
            </Card>
          ))}
        </Stack>
      )}
    </Frame>
  );
}

/** Раздел «Инфо»: статьи по алфавиту, поиск, ссылки «См. также» открывают и показывают нужную статью. */
export function InfoSectionPage() {
  const info = useInfo();
  const code = sectionBySlug(useParams().section);
  const section = info.data?.sections.find((item) => item.code === code);

  if (info.isError && info.data === undefined) {
    return (
      <Frame title={ru.info.title}>
        <Alert color="red">{ru.info.loadError}</Alert>
      </Frame>
    );
  }
  if (info.data === undefined) {
    return (
      <Frame title={ru.info.title}>
        <Loader />
      </Frame>
    );
  }
  if (section === undefined) {
    return (
      <Frame title={ru.info.title}>
        <Text c="dimmed">{ru.info.noSection}</Text>
      </Frame>
    );
  }
  return <Section section={section} all={info.data.sections.flatMap((item) => item.entries)} />;
}

function Section({ section, all }: { section: InfoSectionView; all: InfoEntry[] }) {
  const location = useLocation();
  const anchor = location.hash.replace('#', '');
  const [query, setQuery] = useState('');
  const [opened, setOpened] = useState<string[]>(() => (anchor === '' ? [] : [anchor]));
  const [handled, setHandled] = useState(location.key);
  const resolve = useMemo(() => resolver(all), [all]);
  const shown = section.entries.filter((entry) => matches(entry, query));
  const cards = CARD_SECTIONS.has(section.code);

  // Переход по ссылке «См. также» или из поиска (новый адрес): статья раскрывается, фильтр сбрасывается
  if (handled !== location.key) {
    setHandled(location.key);
    if (anchor !== '') {
      setQuery('');
      setOpened((current) => (current.includes(anchor) ? current : [...current, anchor]));
    }
  }

  // …и прокручивается в видимую область
  useEffect(() => {
    if (anchor === '') return;
    const timer = window.setTimeout(() => document.getElementById(anchor)?.scrollIntoView?.({ block: 'start' }), 50);
    return () => window.clearTimeout(timer);
  }, [anchor, location.key]);

  return (
    <Stack gap="md" data-testid="page-info">
      <BackLink />
      <Title order={2}>{section.title}</Title>
      <SearchField value={query} onChange={setQuery} testId="info-filter" />
      {section.entries.length === 0 && (
        <Text c="dimmed" data-testid="info-empty">
          {ru.info.empty}
        </Text>
      )}
      {section.entries.length > 0 && shown.length === 0 && (
        <Text c="dimmed" data-testid="info-nothing">
          {ru.info.nothing}
        </Text>
      )}
      {cards && (
        // Символы реакций и жетоны окружения: картинка видна без раскрытия (qa № 143, 144)
        <Stack gap="sm">
          {shown.map((entry) => (
            <Card
              key={entry.id}
              withBorder
              padding="sm"
              id={entryAnchor(entry.id)}
              data-testid="info-entry"
              data-entry={entry.id}
              style={
                anchor === entryAnchor(entry.id) ? { borderColor: 'var(--mantine-primary-color-filled)' } : undefined
              }
            >
              <Group align="flex-start" wrap="nowrap" gap="md">
                {entry.imageUrl !== null && (
                  <Image
                    src={entry.imageUrl}
                    alt={entry.title ?? ''}
                    w={72}
                    h={72}
                    fit="contain"
                    radius="sm"
                    data-testid="info-entry-image"
                  />
                )}
                <Stack gap={4} style={{ flex: 1, minWidth: 0 }}>
                  {entry.title !== null && (
                    <Text fw={600} data-testid="info-card-title">
                      {entry.title}
                    </Text>
                  )}
                  <InfoText body={entry.body} resolve={resolve} />
                </Stack>
              </Group>
            </Card>
          ))}
        </Stack>
      )}
      {!cards && (
        <Accordion multiple value={opened} onChange={setOpened} variant="separated">
          {shown.map((entry) => (
            <Accordion.Item
              key={entry.id}
              value={entryAnchor(entry.id)}
              id={entryAnchor(entry.id)}
              data-testid="info-entry"
              data-entry={entry.id}
            >
              <Accordion.Control data-testid="info-entry-title">{entry.title}</Accordion.Control>
              <Accordion.Panel>
                <Stack gap="sm">
                  {entry.imageUrl !== null && (
                    <Image
                      src={entry.imageUrl}
                      alt={entry.title ?? ''}
                      maw={320}
                      radius="sm"
                      data-testid="info-entry-image"
                    />
                  )}
                  <InfoText body={entry.body} resolve={resolve} />
                </Stack>
              </Accordion.Panel>
            </Accordion.Item>
          ))}
        </Accordion>
      )}
    </Stack>
  );
}
