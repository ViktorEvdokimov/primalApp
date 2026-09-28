import { Badge, Button, Checkbox, Group, Loader, Modal, ScrollArea, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useSetOpenQuests } from '../../api/generated/campaigns/campaigns';
import { useQuests } from '../../api/generated/catalog/catalog';
import type { QuestLists } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import type { SheetActions } from './useCampaignSheet';

interface QuestEditorProps {
  campaignId: number;
  quests: QuestLists;
  opened: boolean;
  onClose: () => void;
  actions: SheetActions;
}

/**
 * Редактор открытых заданий (D-5): отмеченные задания становятся открытыми, снятые — закрываются.
 * Выполненные и истёкшие не меняются — их флажки неактивны. «Отмена» ничего не сохраняет.
 */
export function QuestEditor({ opened, onClose, ...props }: QuestEditorProps) {
  return (
    <Modal opened={opened} onClose={onClose} title={ru.sheet.editorTitle} centered scrollAreaComponent={ScrollArea.Autosize}>
      {/* Тело монтируется при каждом открытии: черновик начинается с текущих открытых заданий */}
      <QuestEditorBody onClose={onClose} {...props} />
    </Modal>
  );
}

function QuestEditorBody({ campaignId, quests, onClose, actions }: Omit<QuestEditorProps, 'opened'>) {
  const catalog = useQuests({ query: { staleTime: 60 * 60 * 1000 } });
  const setOpen = useSetOpenQuests();
  const [draft, setDraft] = useState<ReadonlySet<number>>(() => new Set(quests.open.map((quest) => quest.number)));
  const completed = new Set(quests.completed.map((quest) => quest.number));
  const expired = new Set(quests.expired.map((quest) => quest.number));
  // Все задания каталога, как в app: выбора дополнений в v1 нет, задания дополнений открываются вручную
  const shown = catalog.data ?? [];

  const toggle = (number: number, checked: boolean) =>
    setDraft((current) => {
      const next = new Set(current);
      if (checked) next.add(number);
      else next.delete(number);
      return next;
    });

  const save = () => {
    const numbers = [...draft].sort((a, b) => a - b);
    setOpen.mutateAsync({ campaignId, data: { numbers } }).then(
      (lists) => {
        actions.applied((sheet) => ({ ...sheet, quests: lists }));
        onClose();
      },
      (error: unknown) => actions.failed(error),
    );
  };

  return (
    <Stack gap="md" data-testid="quest-editor">
      <Text size="sm" c="dimmed">
        {ru.sheet.editorHint}
      </Text>
      {catalog.isPending && <Loader />}
      <Stack gap={6}>
        {shown.map((quest) => {
          const closed = completed.has(quest.number) || expired.has(quest.number);
          return (
            <Group key={quest.number} gap="xs" wrap="nowrap">
              <Checkbox
                checked={draft.has(quest.number)}
                disabled={closed}
                onChange={(event) => toggle(quest.number, event.currentTarget.checked)}
                label={`${quest.number}. ${quest.name}`}
                data-testid="quest-editor-item"
                data-number={quest.number}
              />
              {completed.has(quest.number) && (
                <Badge size="xs" variant="light" color="gray">
                  {ru.sheet.completedMark}
                </Badge>
              )}
              {expired.has(quest.number) && (
                <Badge size="xs" variant="light" color="gray">
                  {ru.sheet.expiredMark}
                </Badge>
              )}
            </Group>
          );
        })}
      </Stack>
      {/* Кнопки остаются видны при прокрутке длинного списка заданий */}
      <Group justify="flex-end" py="xs" style={{ position: 'sticky', bottom: 0, background: 'var(--mantine-color-body)' }}>
        <Button variant="default" onClick={onClose} data-testid="quest-editor-cancel">
          {ru.sheet.cancel}
        </Button>
        <Button loading={setOpen.isPending} disabled={catalog.data === undefined} onClick={save} data-testid="quest-editor-save">
          {ru.sheet.save}
        </Button>
      </Group>
    </Stack>
  );
}
