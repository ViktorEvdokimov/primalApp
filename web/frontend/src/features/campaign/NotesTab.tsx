import { Stack, Text, Textarea } from '@mantine/core';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/errors';
import { useUpdateCampaign } from '../../api/generated/campaigns/campaigns';
import type { CampaignSheet } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import type { SheetActions } from './useCampaignSheet';

/** Заметки уходят на сервер столько спустя после последнего изменения. */
export const NOTES_DEBOUNCE_MS = 800;
const MAX_NOTES = 20000;

type Status = 'idle' | 'dirty' | 'saving' | 'saved' | 'error';

interface NotesTabProps {
  campaignId: number;
  notes: string;
  actions: SheetActions;
}

/**
 * Заметки с автосохранением: `PATCH /campaigns/{id}` с `expectedVersion` через {@link NOTES_DEBOUNCE_MS} мс
 * после последнего изменения, при потере фокуса и при уходе со страницы. Конфликт версий из-за правки
 * других частей кампании (ресурсы, задания) повторяется с новой версией молча; если на другом устройстве
 * изменили сами заметки — уведомление, а черновик остаётся в поле.
 */
export function NotesTab({ campaignId, notes, actions }: NotesTabProps) {
  const { mutateAsync } = useUpdateCampaign();
  const [draft, setDraft] = useState(notes);
  const [status, setStatus] = useState<Status>('idle');
  const [seenNotes, setSeenNotes] = useState(notes);
  const latest = useRef(notes); // текст в поле — для таймера и ухода со страницы
  const base = useRef(notes); // заметки на сервере, от которых начат черновик
  const saving = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  // Заметки изменились на сервере (другое устройство), а своих несохранённых правок нет — показать новые
  if (notes !== seenNotes) {
    setSeenNotes(notes);
    if (status === 'idle' || status === 'saved') setDraft(notes);
  }
  useEffect(() => {
    if (!saving.current && latest.current === base.current) {
      latest.current = notes;
      base.current = notes;
    }
  }, [notes]);

  const saveRef = useRef<() => Promise<void>>(() => Promise.resolve());
  const save = useCallback(async (): Promise<void> => {
    clearTimeout(timer.current);
    timer.current = undefined;
    if (saving.current) return; // сохранится после ответа на текущий запрос
    const text = latest.current;
    if (text === base.current) return;
    saving.current = true;
    setStatus('saving');
    const send = (expectedVersion: number) => mutateAsync({ id: campaignId, data: { expectedVersion, notes: text } });
    try {
      let sheet: CampaignSheet;
      try {
        sheet = await send(actions.currentVersion());
      } catch (error) {
        const current = error instanceof ApiError ? (error.problem?.current as CampaignSheet | undefined) : undefined;
        if (error instanceof ApiError && error.code === 'VERSION_CONFLICT' && current?.notes === base.current) {
          sheet = await send(current.version);
        } else {
          throw error;
        }
      }
      base.current = text;
      actions.replace(sheet);
      setStatus(latest.current === text ? 'saved' : 'dirty');
    } catch (error) {
      actions.failed(error);
      setStatus('error');
    } finally {
      saving.current = false;
    }
    if (latest.current !== base.current && timer.current === undefined) await saveRef.current();
  }, [mutateAsync, campaignId, actions]);

  useEffect(() => {
    saveRef.current = save;
  }, [save]);

  // Уход со страницы или с вкладки: несохранённое отправляется сразу
  useEffect(() => () => void saveRef.current(), []);
  useEffect(() => {
    if (status !== 'dirty' && status !== 'saving') return undefined;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [status]);

  const onChange = (text: string) => {
    setDraft(text);
    latest.current = text;
    setStatus('dirty');
    clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      timer.current = undefined;
      void saveRef.current();
    }, NOTES_DEBOUNCE_MS);
  };

  const statusText = { idle: '', dirty: '', saving: ru.sheet.notesSaving, saved: ru.sheet.notesSaved, error: ru.sheet.notesError }[
    status
  ];

  return (
    <Stack gap={4}>
      <Textarea
        label={ru.sheet.notes}
        value={draft}
        onChange={(event) => onChange(event.currentTarget.value)}
        onBlur={() => void save()}
        autosize
        minRows={6}
        maxLength={MAX_NOTES}
        data-testid="notes-input"
      />
      <Text size="xs" c={status === 'error' ? 'red' : 'dimmed'} mih={18} aria-live="polite" data-testid="notes-status" data-status={status}>
        {statusText}
      </Text>
    </Stack>
  );
}
