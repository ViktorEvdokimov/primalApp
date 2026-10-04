import {
  ActionIcon,
  Button,
  Card,
  Group,
  Menu,
  MultiSelect,
  NativeSelect,
  NumberInput,
  Select,
  Stack,
  TagsInput,
  Text,
  Textarea,
} from '@mantine/core';
import { ru } from '../../shared/i18n/ru';
import {
  CONDITION_KINDS,
  EFFECT_KINDS,
  newCondition,
  newEffect,
  type ConditionKind,
  type ConditionModel,
  type EffectKind,
  type EffectModel,
} from './effects';

/** Справочники для полей редактора. */
export interface EditorOptions {
  /** Материи и растения: стихии даёт общее правило победы, в эффектах их нет. */
  resources: { value: string; label: string }[];
  quests: { value: string; label: string }[];
  achievements: { value: string; label: string }[];
  bosses: { value: string; label: string }[];
  expansions: { value: string; label: string }[];
}

const CHAPTERS = Array.from({ length: 12 }, (_, chapter) => ({ value: String(chapter), label: String(chapter) }));

interface EffectListProps {
  effects: EffectModel[];
  onChange: (effects: EffectModel[]) => void;
  options: EditorOptions;
  testId?: string;
}

/** Список эффектов: порядок, удаление, добавление по виду; условие «если» содержит такие же списки. */
export function EffectList({ effects, onChange, options, testId = 'effect-list' }: EffectListProps) {
  const replace = (index: number, effect: EffectModel) => onChange(effects.map((item, i) => (i === index ? effect : item)));
  const move = (index: number, shift: number) => {
    const next = [...effects];
    const [item] = next.splice(index, 1);
    if (item !== undefined) next.splice(index + shift, 0, item);
    onChange(next);
  };
  return (
    <Stack gap="xs" data-testid={testId}>
      {effects.length === 0 && (
        <Text size="sm" c="dimmed">
          {ru.admin.empty}
        </Text>
      )}
      {effects.map((effect, index) => (
        <Card key={index} withBorder padding="xs" data-testid="effect" data-kind={effect.kind}>
          <Group justify="space-between" wrap="nowrap" mb={effectHasFields(effect.kind) ? 'xs' : 0}>
            <Text fw={600} size="sm">
              {ru.admin.effectKinds[effect.kind]}
            </Text>
            <Group gap={4} wrap="nowrap">
              <NativeSelect
                size="xs"
                aria-label={ru.admin.expansion}
                value={effect.expansion ?? ''}
                data={[{ value: '', label: ru.admin.noExpansion }, ...options.expansions]}
                onChange={(event) => replace(index, { ...effect, expansion: event.currentTarget.value || null })}
                data-testid="effect-expansion"
              />
              <ActionIcon variant="subtle" disabled={index === 0} onClick={() => move(index, -1)} aria-label={ru.admin.up}>
                ↑
              </ActionIcon>
              <ActionIcon
                variant="subtle"
                disabled={index === effects.length - 1}
                onClick={() => move(index, 1)}
                aria-label={ru.admin.down}
              >
                ↓
              </ActionIcon>
              <ActionIcon
                variant="subtle"
                color="red"
                onClick={() => onChange(effects.filter((_, i) => i !== index))}
                aria-label={ru.admin.remove}
                data-testid="effect-remove"
              >
                ×
              </ActionIcon>
            </Group>
          </Group>
          <EffectFields effect={effect} onChange={(next) => replace(index, next)} options={options} />
        </Card>
      ))}
      <AddMenu
        label={ru.admin.addEffect}
        kinds={EFFECT_KINDS}
        names={ru.admin.effectKinds}
        onAdd={(kind) => onChange([...effects, newEffect(kind)])}
        testId="effect-add"
      />
    </Stack>
  );
}

function effectHasFields(kind: EffectKind): boolean {
  return !['expireAllQuests', 'forgeLevelUp', 'labLevelUp', 'hunterKitUpgrade'].includes(kind);
}

interface AddMenuProps<K extends string> {
  label: string;
  kinds: K[];
  names: Record<string, string>;
  onAdd: (kind: K) => void;
  testId: string;
}

function AddMenu<K extends string>({ label, kinds, names, onAdd, testId }: AddMenuProps<K>) {
  return (
    <Menu position="bottom-start" withinPortal>
      <Menu.Target>
        <Button size="xs" variant="light" style={{ alignSelf: 'flex-start' }} data-testid={testId}>
          + {label}
        </Button>
      </Menu.Target>
      <Menu.Dropdown>
        {kinds.map((kind) => (
          <Menu.Item key={kind} onClick={() => onAdd(kind)} data-testid={`${testId}-${kind}`}>
            {names[kind]}
          </Menu.Item>
        ))}
      </Menu.Dropdown>
    </Menu>
  );
}

interface EffectFieldsProps {
  effect: EffectModel;
  onChange: (effect: EffectModel) => void;
  options: EditorOptions;
}

function EffectFields({ effect, onChange, options }: EffectFieldsProps) {
  switch (effect.kind) {
    case 'resources':
      return (
        <Stack gap={4}>
          {effect.items.map(([code, quantity], index) => (
            <Group key={index} gap="xs" wrap="nowrap" data-testid="effect-resource">
              <Select
                size="xs"
                aria-label={ru.admin.resource}
                placeholder={ru.admin.resource}
                value={code || null}
                data={options.resources.filter(
                  (item) => item.value === code || !effect.items.some(([other]) => other === item.value),
                )}
                onChange={(value) =>
                  onChange({ ...effect, items: effect.items.map((item, i) => (i === index ? [value ?? '', item[1]] : item)) })
                }
                style={{ flex: 1 }}
                data-testid="effect-resource-code"
              />
              <NumberInput
                size="xs"
                aria-label={ru.admin.quantity}
                min={1}
                w={80}
                value={quantity}
                onChange={(value) =>
                  onChange({ ...effect, items: effect.items.map((item, i) => (i === index ? [item[0], Number(value)] : item)) })
                }
                data-testid="effect-resource-quantity"
              />
              <ActionIcon
                variant="subtle"
                color="red"
                aria-label={ru.admin.remove}
                onClick={() => onChange({ ...effect, items: effect.items.filter((_, i) => i !== index) })}
              >
                ×
              </ActionIcon>
            </Group>
          ))}
          <Button
            size="compact-xs"
            variant="subtle"
            style={{ alignSelf: 'flex-start' }}
            onClick={() => onChange({ ...effect, items: [...effect.items, ['', 1]] })}
            data-testid="effect-resource-add"
          >
            + {ru.admin.addResource}
          </Button>
        </Stack>
      );
    case 'openQuest':
      return (
        <Select
          size="xs"
          searchable
          aria-label={ru.admin.questNumber}
          placeholder={ru.admin.questNumber}
          value={effect.quest === null ? null : String(effect.quest)}
          data={options.quests}
          onChange={(value) => onChange({ ...effect, quest: value === null ? null : Number(value) })}
          data-testid="effect-quest"
        />
      );
    case 'expireQuests':
      return (
        <MultiSelect
          size="xs"
          searchable
          aria-label={ru.admin.questsNumbers}
          placeholder={ru.admin.questsNumbers}
          value={effect.quests.map(String)}
          data={options.quests}
          onChange={(value) => onChange({ ...effect, quests: value.map(Number) })}
          data-testid="effect-quests"
        />
      );
    case 'grantAchievement':
      return (
        <Select
          size="xs"
          searchable
          aria-label={ru.admin.achievement}
          placeholder={ru.admin.achievement}
          value={effect.achievement || null}
          data={options.achievements}
          onChange={(value) => onChange({ ...effect, achievement: value ?? '' })}
          data-testid="effect-achievement"
        />
      );
    case 'rewardCards':
      return (
        <TagsInput
          size="xs"
          aria-label={ru.admin.cards}
          placeholder={ru.admin.cards}
          value={effect.cards}
          onChange={(cards) => onChange({ ...effect, cards })}
          data-testid="effect-cards"
        />
      );
    case 'message':
      return (
        <Textarea
          size="xs"
          autosize
          aria-label={ru.admin.message}
          placeholder={ru.admin.message}
          value={effect.text}
          onChange={(event) => onChange({ ...effect, text: event.currentTarget.value })}
          data-testid="effect-message"
        />
      );
    case 'finalBattle':
      return (
        <Select
          size="xs"
          searchable
          aria-label={ru.admin.finalBoss}
          placeholder={ru.admin.finalBoss}
          value={effect.boss || null}
          data={options.bosses}
          onChange={(value) => onChange({ ...effect, boss: value ?? '' })}
          data-testid="effect-boss"
        />
      );
    case 'if':
      return (
        <Stack gap="xs">
          <ConditionEditor
            condition={effect.condition}
            onChange={(condition) => onChange({ ...effect, condition })}
            options={options}
          />
          <Text size="sm" fw={600}>
            {ru.admin.then}
          </Text>
          <div style={{ paddingLeft: 12, borderLeft: '2px solid var(--mantine-color-teal-4)' }}>
            <EffectList effects={effect.then} onChange={(then) => onChange({ ...effect, then })} options={options} testId="effect-then" />
          </div>
          <Text size="sm" fw={600}>
            {ru.admin.otherwise}
          </Text>
          <div style={{ paddingLeft: 12, borderLeft: '2px solid var(--mantine-color-orange-4)' }}>
            <EffectList
              effects={effect.otherwise}
              onChange={(otherwise) => onChange({ ...effect, otherwise })}
              options={options}
              testId="effect-else"
            />
          </div>
        </Stack>
      );
    default:
      return null;
  }
}

interface ConditionEditorProps {
  condition: ConditionModel;
  onChange: (condition: ConditionModel) => void;
  options: EditorOptions;
  onRemove?: () => void;
}

/** Условие: вид и его поля; «не», «все», «любое» содержат вложенные условия. */
export function ConditionEditor({ condition, onChange, options, onRemove }: ConditionEditorProps) {
  return (
    <Stack gap={4} data-testid="condition" data-kind={condition.kind}>
      <Group gap="xs" wrap="nowrap">
        <NativeSelect
          size="xs"
          aria-label={ru.admin.condition}
          value={condition.kind}
          data={CONDITION_KINDS.map((kind) => ({ value: kind, label: ru.admin.conditionKinds[kind] ?? kind }))}
          onChange={(event) => onChange(newCondition(event.currentTarget.value as ConditionKind))}
          data-testid="condition-kind"
        />
        <div style={{ flex: 1 }}>
          <ConditionFields condition={condition} onChange={onChange} options={options} />
        </div>
        {onRemove !== undefined && (
          <ActionIcon variant="subtle" color="red" aria-label={ru.admin.remove} onClick={onRemove}>
            ×
          </ActionIcon>
        )}
      </Group>
      {(condition.kind === 'not' || condition.kind === 'all' || condition.kind === 'any') && (
        <div style={{ paddingLeft: 12, borderLeft: '2px dashed var(--mantine-color-gray-5)' }}>
          {condition.kind === 'not' ? (
            <ConditionEditor
              condition={condition.condition}
              onChange={(inner) => onChange({ ...condition, condition: inner })}
              options={options}
            />
          ) : (
            <Stack gap={4}>
              {condition.conditions.map((inner, index) => (
                <ConditionEditor
                  key={index}
                  condition={inner}
                  options={options}
                  onChange={(next) =>
                    onChange({ ...condition, conditions: condition.conditions.map((item, i) => (i === index ? next : item)) })
                  }
                  onRemove={
                    condition.conditions.length > 1
                      ? () => onChange({ ...condition, conditions: condition.conditions.filter((_, i) => i !== index) })
                      : undefined
                  }
                />
              ))}
              <Button
                size="compact-xs"
                variant="subtle"
                style={{ alignSelf: 'flex-start' }}
                onClick={() => onChange({ ...condition, conditions: [...condition.conditions, newCondition('achievement')] })}
                data-testid="condition-add"
              >
                + {ru.admin.addCondition}
              </Button>
            </Stack>
          )}
        </div>
      )}
    </Stack>
  );
}

function ConditionFields({ condition, onChange, options }: Omit<ConditionEditorProps, 'onRemove'>) {
  switch (condition.kind) {
    case 'achievement':
      return (
        <Select
          size="xs"
          searchable
          aria-label={ru.admin.achievement}
          placeholder={ru.admin.achievement}
          value={condition.achievement || null}
          data={options.achievements}
          onChange={(value) => onChange({ ...condition, achievement: value ?? '' })}
          data-testid="condition-achievement"
        />
      );
    case 'chapterIn':
      return (
        <MultiSelect
          size="xs"
          aria-label={ru.admin.chapters}
          placeholder={ru.admin.chapters}
          value={condition.chapters.map(String)}
          data={CHAPTERS}
          onChange={(value) => onChange({ ...condition, chapters: value.map(Number).sort((a, b) => a - b) })}
          data-testid="condition-chapters"
        />
      );
    case 'questAvailable':
      return (
        <Select
          size="xs"
          searchable
          aria-label={ru.admin.questNumber}
          placeholder={ru.admin.questNumber}
          value={condition.quest === null ? null : String(condition.quest)}
          data={options.quests}
          onChange={(value) => onChange({ ...condition, quest: value === null ? null : Number(value) })}
          data-testid="condition-quest"
        />
      );
    case 'expansion':
      return (
        <Select
          size="xs"
          aria-label={ru.admin.expansion}
          placeholder={ru.admin.expansion}
          value={condition.expansion || null}
          data={options.expansions}
          onChange={(value) => onChange({ ...condition, expansion: value ?? '' })}
          data-testid="condition-expansion"
        />
      );
    default:
      return null;
  }
}
