import {
  Alert,
  Button,
  Card,
  FileButton,
  Group,
  Image,
  Loader,
  Modal,
  NativeSelect,
  SegmentedControl,
  Select,
  Stack,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import {
  useCreateInfoEntry,
  useDeleteInfoEntry,
  useImportInfoKeywords,
  exportInfo,
  useRemoveInfoImage,
  useRestoreInfo,
  useUpdateInfoEntry,
  useUploadInfoImage,
} from '../../api/generated/admin/admin';
import { getGetInfoQueryKey, useGetInfo } from '../../api/generated/info/info';
import type { InfoEntry, InfoEntrySection, InfoView } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { errorMessage } from '../campaign/useCampaignSheet';
import { InfoText } from '../info/InfoText';
import { entryLabel, resolver } from '../info/infoModel';

const IMAGE_MAX_BYTES = 2 * 1024 * 1024;
const NEW = 'new';

/**
 * «Инфо» в администрировании (qa № 142): статьи всех разделов — добавить, изменить, удалить, картинка; ключевые
 * слова можно импортировать из Markdown-файла правил.
 */
export function InfoEditor() {
  const info = useGetInfo({ query: { staleTime: 0 } });
  const [section, setSection] = useState<InfoEntrySection>('KEYWORDS');
  const [selected, setSelected] = useState<string | null>(null);
  // Названия разделов длинные: на телефоне — столбиком
  const narrow = useMediaQuery('(max-width: 48em)') ?? false;

  if (info.isError) return <Alert color="red">{ru.info.loadError}</Alert>;
  if (info.data === undefined) return <Loader />;

  const entries = info.data.sections.find((item) => item.code === section)?.entries ?? [];
  const entry = entries.find((item) => String(item.id) === selected);
  const all = info.data.sections.flatMap((item) => item.entries);

  return (
    <Stack gap="md" data-testid="admin-info">
      <SegmentedControl
        fullWidth
        orientation={narrow ? 'vertical' : 'horizontal'}
        value={section}
        onChange={(value) => {
          setSection(value as InfoEntrySection);
          setSelected(null);
        }}
        data={info.data.sections.map((item) => ({ value: item.code, label: item.title }))}
        data-testid="admin-info-section"
      />
      {section === 'KEYWORDS' && <ImportPanel />}
      <TransferPanel />
      <Group align="flex-end" wrap="nowrap">
        <Select
          label={ru.adminInfo.entry}
          placeholder={entries.length === 0 ? ru.info.empty : ru.adminInfo.choose}
          searchable
          value={selected === NEW ? null : selected}
          data={entries.map((item) => ({ value: String(item.id), label: entryLabel(item) }))}
          onChange={setSelected}
          style={{ flex: 1 }}
          data-testid="admin-info-entry"
        />
        <Button variant="light" onClick={() => setSelected(NEW)} data-testid="admin-info-new">
          + {ru.adminInfo.add}
        </Button>
      </Group>
      {selected === NEW && (
        <EntryForm
          key={`new-${section}`}
          section={section}
          entry={null}
          all={all}
          onSaved={(saved) => setSelected(String(saved.id))}
          onDeleted={() => setSelected(null)}
        />
      )}
      {entry !== undefined && (
        <EntryForm
          key={entry.id}
          section={section}
          entry={entry}
          all={all}
          onSaved={(saved) => {
            setSection(saved.section);
            setSelected(String(saved.id));
          }}
          onDeleted={() => setSelected(null)}
        />
      )}
    </Stack>
  );
}

/**
 * Перенос «Инфо» (qa № 145): «Выгрузить всё» — файл со статьями и картинками (тот же, что
 * `deploy/export-info.sh` кладёт в проект как «Инфо» по умолчанию); «Заменить всё из выгрузки» — с подтверждением.
 */
function TransferPanel() {
  const queryClient = useQueryClient();
  const restore = useRestoreInfo();
  const [downloading, setDownloading] = useState(false);
  const [pending, setPending] = useState<File | null>(null);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const download = () => {
    setMessage(null);
    setDownloading(true);
    exportInfo()
      .then(
        (snapshot) => {
          const url = URL.createObjectURL(new Blob([JSON.stringify(snapshot, null, 2)], { type: 'application/json' }));
          const link = document.createElement('a');
          link.href = url;
          link.download = 'default-info.json';
          link.click();
          URL.revokeObjectURL(url);
        },
        (cause: unknown) => setMessage({ ok: false, text: errorMessage(cause) }),
      )
      .finally(() => setDownloading(false));
  };

  const replace = (file: File) => {
    setPending(null);
    setMessage(null);
    restore.mutateAsync({ data: { file } }).then(
      (result) => {
        setMessage({ ok: true, text: ru.adminInfo.restored(result.restored) });
        void queryClient.invalidateQueries({ queryKey: getGetInfoQueryKey() });
      },
      (cause: unknown) => setMessage({ ok: false, text: errorMessage(cause) }),
    );
  };

  return (
    <Card withBorder padding="sm" data-testid="admin-info-transfer">
      <Stack gap="xs">
        <Text size="sm">{ru.adminInfo.transferHint}</Text>
        <Group>
          <Button variant="default" loading={downloading} onClick={download} data-testid="admin-info-export">
            {ru.adminInfo.export}
          </Button>
          <FileButton onChange={setPending} accept=".json,application/json">
            {(props) => (
              <Button
                {...props}
                variant="subtle"
                color="red"
                loading={restore.isPending}
                data-testid="admin-info-restore"
              >
                {ru.adminInfo.restore}
              </Button>
            )}
          </FileButton>
        </Group>
        {message !== null && (
          <Alert color={message.ok ? 'teal' : 'red'} data-testid="admin-info-transfer-result">
            {message.text}
          </Alert>
        )}
      </Stack>
      <Modal opened={pending !== null} onClose={() => setPending(null)} title={ru.adminInfo.restore} centered>
        <Stack gap="md">
          <Text size="sm">{ru.adminInfo.restoreConfirm(pending?.name ?? '')}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setPending(null)}>
              {ru.inventory.cancel}
            </Button>
            <Button
              color="red"
              onClick={() => pending !== null && replace(pending)}
              data-testid="admin-info-restore-confirm"
            >
              {ru.adminInfo.restore}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Card>
  );
}

/** Импорт ключевых слов: из раздела «Ключевые слова» файла правил добавляются статьи, которых ещё нет. */
function ImportPanel() {
  const queryClient = useQueryClient();
  const importKeywords = useImportInfoKeywords();
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const upload = (file: File | null) => {
    if (file === null) return;
    setMessage(null);
    importKeywords.mutateAsync({ data: { file } }).then(
      (result) => {
        setMessage({ ok: true, text: ru.adminInfo.imported(result.found, result.created, result.skipped) });
        void queryClient.invalidateQueries({ queryKey: getGetInfoQueryKey() });
      },
      (cause: unknown) => setMessage({ ok: false, text: errorMessage(cause) }),
    );
  };

  return (
    <Card withBorder padding="sm">
      <Stack gap="xs">
        <Text size="sm">{ru.adminInfo.importHint}</Text>
        <FileButton onChange={upload} accept=".md,text/markdown,text/plain">
          {(props) => (
            <Button
              {...props}
              variant="default"
              loading={importKeywords.isPending}
              style={{ alignSelf: 'flex-start' }}
              data-testid="admin-info-import"
            >
              {ru.adminInfo.import}
            </Button>
          )}
        </FileButton>
        {message !== null && (
          <Alert color={message.ok ? 'teal' : 'red'} data-testid="admin-info-import-result">
            {message.text}
          </Alert>
        )}
      </Stack>
    </Card>
  );
}

interface EntryFormProps {
  section: InfoEntrySection;
  entry: InfoEntry | null;
  all: InfoEntry[];
  onSaved: (entry: InfoEntry) => void;
  onDeleted: () => void;
}

function EntryForm({ section, entry, all, onSaved, onDeleted }: EntryFormProps) {
  const queryClient = useQueryClient();
  const create = useCreateInfoEntry();
  const update = useUpdateInfoEntry();
  const remove = useDeleteInfoEntry();
  const uploadImage = useUploadInfoImage();
  const removeImage = useRemoveInfoImage();
  const [title, setTitle] = useState(entry?.title ?? '');
  const [body, setBody] = useState(entry?.body ?? '');
  const [target, setTarget] = useState<InfoEntrySection>(entry?.section ?? section);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const resolve = useMemo(() => resolver(all), [all]);
  // У символа реакции названия нет — только картинка и описание (qa № 143)
  const titled = target !== 'REACTIONS';
  const dirty = entry === null || (titled && title !== entry.title) || body !== entry.body || target !== entry.section;

  /** Ответ сервера — в кэш «Инфо»: статья заменяется (или добавляется) в своём разделе. */
  const store = (saved: InfoEntry) => {
    queryClient.setQueryData<InfoView>(getGetInfoQueryKey(), (current) =>
      current === undefined
        ? current
        : {
            ...current,
            sections: current.sections.map((item) => {
              const others = item.entries.filter((other) => other.id !== saved.id);
              const entries = item.code === saved.section ? [...others, saved] : others;
              // Как на сервере: по алфавиту, символы реакций (без названия) — в порядке добавления
              return {
                ...item,
                entries: entries.sort((a, b) =>
                  a.title !== null && b.title !== null ? a.title.localeCompare(b.title, 'ru') : a.id - b.id,
                ),
              };
            }),
          },
    );
  };
  const failed = (cause: unknown) => setError(errorMessage(cause));

  const save = () => {
    setError(null);
    const data = { section: target, title: titled ? title : null, body };
    const request = entry === null ? create.mutateAsync({ data }) : update.mutateAsync({ id: entry.id, data });
    request.then((saved) => {
      store(saved);
      notifications.show({ color: 'teal', message: ru.adminInfo.saved });
      onSaved(saved);
    }, failed);
  };

  const pickImage = (file: File | null) => {
    if (file === null || entry === null) return;
    setError(null);
    if (file.size > IMAGE_MAX_BYTES) {
      setError(ru.adminInfo.imageTooBig);
      return;
    }
    uploadImage.mutateAsync({ id: entry.id, data: { file } }).then(store, failed);
  };

  return (
    <Card withBorder padding="md" data-testid="admin-info-form">
      <Stack gap="sm">
        <Group justify="space-between">
          <Title order={4}>{entry === null ? ru.adminInfo.newTitle : entryLabel(entry)}</Title>
          {entry !== null && (
            <Button variant="subtle" color="red" onClick={() => setConfirming(true)} data-testid="admin-info-delete">
              {ru.adminInfo.delete}
            </Button>
          )}
        </Group>
        {titled ? (
          <TextInput
            label={ru.adminInfo.title}
            value={title}
            maxLength={120}
            onChange={(event) => setTitle(event.currentTarget.value)}
            data-testid="admin-info-title"
          />
        ) : (
          <Text size="xs" c="dimmed" data-testid="admin-info-no-title">
            {ru.adminInfo.noTitle}
          </Text>
        )}
        <NativeSelect
          label={ru.adminInfo.section}
          value={target}
          data={[
            { value: 'KEYWORDS', label: ru.adminInfo.sections.KEYWORDS },
            { value: 'REACTIONS', label: ru.adminInfo.sections.REACTIONS },
            { value: 'TOKENS', label: ru.adminInfo.sections.TOKENS },
          ]}
          onChange={(event) => setTarget(event.currentTarget.value as InfoEntrySection)}
          data-testid="admin-info-target"
        />
        <Textarea
          label={ru.adminInfo.body}
          description={ru.adminInfo.bodyHint}
          autosize
          minRows={6}
          value={body}
          onChange={(event) => setBody(event.currentTarget.value)}
          data-testid="admin-info-body"
        />
        <Stack gap={4}>
          <Text size="sm" fw={500}>
            {ru.adminInfo.image}
          </Text>
          {entry?.imageUrl != null && (
            <Image src={entry.imageUrl} alt={entry.title ?? ''} maw={240} radius="sm" data-testid="admin-info-image" />
          )}
          {entry === null ? (
            <Text size="xs" c="dimmed">
              {ru.adminInfo.imageAfterSave}
            </Text>
          ) : (
            <Group>
              <FileButton onChange={pickImage} accept="image/png,image/jpeg,image/webp,image/gif">
                {(props) => (
                  <Button
                    {...props}
                    size="xs"
                    variant="default"
                    loading={uploadImage.isPending}
                    data-testid="admin-info-image-upload"
                  >
                    {entry.imageUrl === null ? ru.adminInfo.imageAdd : ru.adminInfo.imageReplace}
                  </Button>
                )}
              </FileButton>
              {entry.imageUrl !== null && (
                <Button
                  size="xs"
                  variant="subtle"
                  color="red"
                  loading={removeImage.isPending}
                  onClick={() => removeImage.mutateAsync({ id: entry.id }).then(store, failed)}
                  data-testid="admin-info-image-remove"
                >
                  {ru.adminInfo.imageRemove}
                </Button>
              )}
            </Group>
          )}
        </Stack>
        <Stack gap={4} data-testid="admin-info-preview">
          <Text size="xs" c="dimmed">
            {ru.adminInfo.preview}
          </Text>
          <InfoText body={body} resolve={resolve} />
        </Stack>
        {error !== null && (
          <Alert color="red" data-testid="admin-info-error">
            {error}
          </Alert>
        )}
        <Group>
          <Button
            disabled={!dirty || (titled && title.trim() === '')}
            loading={create.isPending || update.isPending}
            onClick={save}
            data-testid="admin-info-save"
          >
            {ru.admin.save}
          </Button>
        </Group>
      </Stack>
      <Modal opened={confirming} onClose={() => setConfirming(false)} title={ru.adminInfo.delete} centered>
        <Stack gap="md">
          <Text size="sm">{ru.adminInfo.deleteConfirm(entry === null ? '' : entryLabel(entry))}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setConfirming(false)}>
              {ru.inventory.cancel}
            </Button>
            <Button
              color="red"
              loading={remove.isPending}
              onClick={() => {
                if (entry === null) return;
                remove.mutateAsync({ id: entry.id }).then(() => {
                  setConfirming(false);
                  queryClient.setQueryData<InfoView>(getGetInfoQueryKey(), (current) =>
                    current === undefined
                      ? current
                      : {
                          ...current,
                          sections: current.sections.map((item) => ({
                            ...item,
                            entries: item.entries.filter((other) => other.id !== entry.id),
                          })),
                        },
                  );
                  notifications.show({ color: 'teal', message: ru.adminInfo.deleted });
                  onDeleted();
                }, failed);
              }}
              data-testid="admin-info-delete-confirm"
            >
              {ru.adminInfo.delete}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Card>
  );
}
