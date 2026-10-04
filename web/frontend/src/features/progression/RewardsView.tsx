import { Badge, Group, Image, Stack, Text } from '@mantine/core';
import { useDictionaries } from '../../api/generated/catalog/catalog';
import type { AchievementRef, ExpiringQuest, RewardRule } from '../../api/generated/primal.schemas';
import { iconUrl } from '../../shared/assets';
import { ru } from '../../shared/i18n/ru';

interface RewardsViewProps {
  /** Строки вверху окна: трофей, кузня, финальный бой, сообщения. */
  lines: string[];
  perHunter: Record<string, number>;
  openQuests: number[];
  /** Открытые задания, у которых истечёт время, с последствиями невыполненного задания. */
  expireQuests?: ExpiringQuest[];
  achievements: AchievementRef[];
  rewardCards: string[];
  rules: RewardRule[];
}

/**
 * Награды боя или главы, как окна наград app: безусловные награды, затем «Условные задания:» и «Условные
 * награды:» с результатом проверки для этой кампании. Формулировки правил строит сервер.
 */
export function RewardsView({ lines, perHunter, openQuests, expireQuests = [], achievements, rewardCards, rules }: RewardsViewProps) {
  const dictionaries = useDictionaries({ query: { staleTime: 60 * 60 * 1000 } });
  const names = new Map(
    [...(dictionaries.data?.materials ?? []), ...(dictionaries.data?.plants ?? []), ...(dictionaries.data?.elements ?? [])].map(
      (item) => [item.code, item.name],
    ),
  );
  const resources = Object.entries(perHunter);
  const questRules = rules.filter((rule) => rule.kind === 'QUEST');
  const rewardRules = rules.filter((rule) => rule.kind !== 'QUEST');
  const empty =
    lines.length === 0 &&
    resources.length === 0 &&
    openQuests.length === 0 &&
    expireQuests.length === 0 &&
    achievements.length === 0 &&
    rewardCards.length === 0 &&
    rules.length === 0;

  return (
    <Stack gap="sm" data-testid="rewards">
      {empty && (
        <Text c="dimmed" data-testid="rewards-empty">
          {ru.progression.noRewards}
        </Text>
      )}
      {lines.map((line) => (
        <Text key={line} data-testid="rewards-line">
          {line}
        </Text>
      ))}
      {resources.length > 0 && (
        <Stack gap={4}>
          <Text fw={600}>{ru.progression.perHunter}</Text>
          <Group gap="xs">
            {resources.map(([code, quantity]) => {
              const icon = iconUrl(code);
              return (
                <Badge
                  key={code}
                  variant="default"
                  size="lg"
                  leftSection={icon === null ? undefined : <Image src={icon} alt="" w={16} h={16} />}
                  data-testid="rewards-resource"
                  data-code={code}
                  data-quantity={quantity}
                  style={{ textTransform: 'none' }}
                >
                  {`${names.get(code) ?? code} ${quantity}`}
                </Badge>
              );
            })}
          </Group>
        </Stack>
      )}
      {openQuests.length > 0 && (
        <Text data-testid="rewards-open-quests">{`${ru.progression.openQuests} ${openQuests.join(', ')}`}</Text>
      )}
      {expireQuests.length > 0 && (
        <Stack gap={2}>
          <Text data-testid="rewards-expire-quests">
            {`${ru.progression.expireQuests} ${expireQuests.map((quest) => quest.number).join(', ')}`}
          </Text>
          <Text size="sm" c="dimmed" data-testid="rewards-expiry-note">
            {ru.progression.expiryNote}
          </Text>
          {expireQuests.map((quest) => (
            <Text key={quest.number} size="sm" pl="md" data-testid="rewards-expiry" data-quest={quest.number}>
              {ru.progression.expiry(quest.number, quest.name, quest.consequences)}
            </Text>
          ))}
        </Stack>
      )}
      {achievements.length > 0 && (
        <Text data-testid="rewards-achievements">
          {`${ru.progression.achievements} ${achievements.map((achievement) => `«${achievement.name}»`).join(', ')}`}
        </Text>
      )}
      {rewardCards.length > 0 && (
        <Text data-testid="rewards-cards">{ru.progression.rewardCards(rewardCards.join(', '))}</Text>
      )}
      <RuleGroup title={ru.progression.questRules} rules={questRules} />
      <RuleGroup title={ru.progression.rewardRules} rules={rewardRules} />
    </Stack>
  );
}

function RuleGroup({ title, rules }: { title: string; rules: RewardRule[] }) {
  if (rules.length === 0) return null;
  return (
    <Stack gap={4}>
      <Text fw={600}>{title}</Text>
      {rules.map((rule) => (
        <Stack key={rule.description} gap={0} data-testid="rewards-rule" data-kind={rule.kind}>
          <Text size="sm">{rule.description}</Text>
          <Text size="sm" c="dimmed">
            → {rule.result}
          </Text>
        </Stack>
      ))}
    </Stack>
  );
}
