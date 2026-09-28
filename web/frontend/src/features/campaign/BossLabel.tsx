import { Group, Image, Text } from '@mantine/core';
import type { CampaignBoss } from '../../api/generated/primal.schemas';
import { iconUrl } from '../../shared/assets';

/** Босс с иконкой стихии; у Пробуждённого стихии нет. */
export function BossLabel({ boss }: { boss: CampaignBoss }) {
  const icon = boss.element ? iconUrl(boss.element) : null;
  return (
    <Group gap={4} wrap="nowrap">
      {icon && <Image src={icon} alt="" w={18} h={18} fit="contain" />}
      <Text size="sm" c="dimmed">
        {boss.name}
      </Text>
    </Group>
  );
}
