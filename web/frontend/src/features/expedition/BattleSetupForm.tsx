import { Alert, Button, Group, Loader, SegmentedControl, Stack, Text, TextInput } from '@mantine/core';
import { useState } from 'react';
import type { Boss } from '../../api/generated/primal.schemas';
import { invalidValueMessages, type Difficulty, type StanceChange } from '../../domain/battle';
import { ru } from '../../shared/i18n/ru';
import { availableDifficulties, firstStance, isDifficulty, useBossCatalog } from '../battle/bossCatalog';
import { StanceFields } from '../battle/StanceFields';
import {
  MANUAL_STANCE,
  parseHunterCount,
  parseStanceForm,
  type StanceFormErrors,
  type StanceFormValues,
} from '../battle/stanceForm';
import { BossSelect, MANUAL_BOSS } from './BossSelect';
import { ReactionDeckHint } from './ReactionDeckHint';

const DIFFICULTY_LEVELS: Difficulty[] = [0, 1, 2, 3];

/** Проверенные параметры боя из формы подготовки. */
export interface BattleSetupValues {
  boss: Boss | undefined;
  difficulty: Difficulty;
  hunterCount: number;
  toughnessPerHunter: number | null;
  stanceChange: StanceChange;
}

interface BattleSetupFormProps {
  /** Префикс data-testid полей: `expedition-difficulty`, `campaign-battle-hunters`. */
  testIdPrefix: string;
  initialBoss?: string;
  initialDifficulty?: Difficulty;
  initialHunters: string;
  /** Пролог и финальный бой: босс задан, выбрать другого нельзя. */
  bossLocked?: boolean;
  onStart: (values: BattleSetupValues) => void;
}

/**
 * Подготовка к бою — общая для экспедиции и боя кампании (app/doc/behavior.md §1): босс, уровень
 * враждебности, охотники и стойка I с префиллом из каталога. Каталог боссов должен быть уже загружен, если
 * задан `initialBoss`: префилл стойки берётся при монтировании.
 */
export function BattleSetupForm({
  testIdPrefix,
  initialBoss = MANUAL_BOSS,
  initialDifficulty = 0,
  initialHunters,
  bossLocked = false,
  onStart,
}: BattleSetupFormProps) {
  const catalog = useBossCatalog();
  const [bossCode, setBossCode] = useState(initialBoss);
  const [difficulty, setDifficulty] = useState<Difficulty>(initialDifficulty);
  const [hunters, setHunters] = useState(initialHunters);
  const [stance, setStance] = useState<StanceFormValues>(() =>
    firstStance(catalog.bosses?.find((candidate) => candidate.code === initialBoss), initialDifficulty),
  );
  const [huntersError, setHuntersError] = useState<string | undefined>();
  const [stanceErrors, setStanceErrors] = useState<StanceFormErrors>({});

  const boss = catalog.bosses?.find((candidate) => candidate.code === bossCode);
  const levels = boss === undefined ? DIFFICULTY_LEVELS : availableDifficulties(boss);

  const selectBoss = (code: string) => {
    setBossCode(code);
    setStanceErrors({});
    const next = catalog.bosses?.find((candidate) => candidate.code === code);
    if (next === undefined) {
      setStance(MANUAL_STANCE);
      return;
    }
    // Нет выбранного уровня — берётся единственный доступный (Пробуждённый — 3)
    const nextLevels = availableDifficulties(next);
    const level = nextLevels.includes(difficulty) ? difficulty : (nextLevels[0] ?? difficulty);
    setDifficulty(level);
    setStance(firstStance(next, level));
  };

  const selectDifficulty = (value: string) => {
    const level = Number(value);
    if (!isDifficulty(level)) return;
    setDifficulty(level);
    setStanceErrors({});
    if (boss !== undefined) setStance(firstStance(boss, level));
  };

  const start = () => {
    const hunterCount = parseHunterCount(hunters);
    const parsed = parseStanceForm(stance);
    setHuntersError(hunterCount === null ? invalidValueMessages.hunterCount : undefined);
    setStanceErrors(parsed.ok ? {} : parsed.errors);
    if (hunterCount === null || !parsed.ok) return;
    onStart({
      boss,
      difficulty,
      hunterCount,
      toughnessPerHunter: parsed.toughnessPerHunter,
      stanceChange: parsed.stanceChange,
    });
  };

  return (
    <Stack gap="md">
      {catalog.isLoading && (
        <Group gap="xs" data-testid={`${testIdPrefix}-catalog-loading`}>
          <Loader size="xs" />
          <Text size="sm" c="dimmed">
            {ru.expedition.catalogLoading}
          </Text>
        </Group>
      )}
      {catalog.isError && (
        <Alert color="red" data-testid={`${testIdPrefix}-catalog-error`}>
          <Stack gap="xs" align="flex-start">
            <Text size="sm">{ru.expedition.catalogError}</Text>
            <Button
              size="xs"
              variant="light"
              onClick={() => void catalog.refetch()}
              data-testid={`${testIdPrefix}-catalog-retry`}
            >
              {ru.expedition.retry}
            </Button>
          </Stack>
        </Alert>
      )}

      <BossSelect
        bosses={catalog.bosses ?? []}
        elementNames={catalog.elementNames}
        value={bossCode}
        onChange={selectBoss}
        disabled={bossLocked}
        testId={`${testIdPrefix}-boss`}
      />

      <div>
        <Text size="sm" fw={500} mb={4}>
          {ru.expedition.difficulty}
        </Text>
        <SegmentedControl
          fullWidth
          value={String(difficulty)}
          onChange={selectDifficulty}
          data={DIFFICULTY_LEVELS.map((level) => ({
            value: String(level),
            label: String(level),
            disabled: !levels.includes(level),
          }))}
          data-testid={`${testIdPrefix}-difficulty`}
        />
      </div>
      <ReactionDeckHint difficulty={difficulty} />

      <TextInput
        label={ru.expedition.hunters}
        inputMode="numeric"
        value={hunters}
        error={huntersError}
        onChange={(event) => setHunters(event.currentTarget.value)}
        data-testid={`${testIdPrefix}-hunters`}
      />

      <StanceFields values={stance} errors={stanceErrors} onChange={setStance} testIdPrefix={testIdPrefix} />

      <Button size="lg" onClick={start} data-testid={`${testIdPrefix}-start`}>
        {ru.expedition.start}
      </Button>
    </Stack>
  );
}
