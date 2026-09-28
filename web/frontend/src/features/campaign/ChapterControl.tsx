import { Alert, Button, Group, Modal, NumberInput, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useUpdateCampaign } from '../../api/generated/campaigns/campaigns';
import { ru } from '../../shared/i18n/ru';
import type { SheetActions } from './useCampaignSheet';

export const MAX_CHAPTER = 11;

interface ChapterControlProps {
  campaignId: number;
  chapter: number;
  /** Глава завершена и ждёт перехода — ручная правка недоступна (`409 CHAPTER_TRANSITION_PENDING`). */
  pendingTransition: boolean;
  actions: SheetActions;
}

/**
 * Ручная правка главы (0 — пролог, до 11) — для исправления ошибок, с подтверждением: правка растит
 * `progress_seq`, и бои, начатые раньше, устаревают (doc/api.md §5.3).
 */
export function ChapterControl({ campaignId, chapter, pendingTransition, actions }: ChapterControlProps) {
  const [opened, setOpened] = useState(false);
  const [value, setValue] = useState<number>(chapter);
  const update = useUpdateCampaign();
  const valid = Number.isInteger(value) && value >= 0 && value <= MAX_CHAPTER;

  const open = () => {
    setValue(chapter);
    setOpened(true);
  };

  const save = () => {
    if (!valid) return;
    if (value === chapter) {
      setOpened(false);
      return;
    }
    update.mutateAsync({ id: campaignId, data: { expectedVersion: actions.currentVersion(), chapter: value } }).then(
      (sheet) => {
        actions.replace(sheet);
        setOpened(false);
      },
      (error: unknown) => {
        actions.failed(error);
        setOpened(false);
      },
    );
  };

  return (
    <>
      <Button size="xs" variant="subtle" disabled={pendingTransition} onClick={open} data-testid="chapter-edit">
        {ru.sheet.changeChapter}
      </Button>
      <Modal opened={opened} onClose={() => setOpened(false)} title={ru.sheet.chapterTitle} centered>
        <Stack gap="md">
          <Alert color="yellow" variant="light">
            <Text size="sm">{ru.sheet.chapterWarning}</Text>
          </Alert>
          <NumberInput
            label={ru.sheet.chapterLabel}
            description={ru.campaigns.chapter(valid ? value : chapter)}
            value={value}
            onChange={(next) => setValue(typeof next === 'number' ? next : Number.NaN)}
            min={0}
            max={MAX_CHAPTER}
            allowDecimal={false}
            allowNegative={false}
            clampBehavior="strict"
            data-testid="chapter-input"
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setOpened(false)} data-testid="chapter-cancel">
              {ru.sheet.cancel}
            </Button>
            <Button loading={update.isPending} disabled={!valid} onClick={save} data-testid="chapter-save">
              {ru.sheet.save}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
