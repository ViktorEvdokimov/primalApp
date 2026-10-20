import { Image, Modal } from '@mantine/core';
import { ru } from '../i18n/ru';
import type { EquipmentCard } from './equipmentCards';

interface CardDialogProps {
  card: EquipmentCard | null;
  onClose: () => void;
}

/** Окно «Описание»: карта снаряжения из файлов игры целиком (кузница, лаборатория). */
export function CardDialog({ card, onClose }: CardDialogProps) {
  const title = card === null ? '' : ru.cards.title(card.name, card.level);
  return (
    <Modal opened={card !== null} onClose={onClose} title={title} centered size="auto" data-testid="card-dialog">
      {card !== null && (
        <Image src={card.url} alt={title} fit="contain" mah="78vh" w="auto" maw="100%" data-testid="card-image" />
      )}
    </Modal>
  );
}
