import { Image, Stack, Text } from '@mantine/core';
import { reactionDeckUrl } from '../../shared/assets';
import { ru } from '../../shared/i18n/ru';

/** Какую колоду карт реакций подготовить для уровня враждебности 0–3 (как ReactionDeckHint в app). */
export function ReactionDeckHint({ difficulty }: { difficulty: number }) {
  return (
    <Stack gap={4}>
      <Text size="sm">{ru.reactionDeck.title}</Text>
      {/* Руны на белом фоне, 878×361 */}
      <Image
        src={reactionDeckUrl(difficulty)}
        alt={ru.reactionDeck.alt(difficulty)}
        h={72}
        w="auto"
        fit="contain"
        radius="sm"
        bg="white"
        style={{ alignSelf: 'flex-start' }}
        data-testid="expedition-deck"
      />
    </Stack>
  );
}
