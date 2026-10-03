import { ActionIcon, Alert, Badge, Button, Group, Modal, NativeSelect, Stack, Text, TextInput, Title } from '@mantine/core';
import { useState } from 'react';
import { useAddItem, useEditItem, useRemoveItem } from '../../api/generated/campaigns/campaigns';
import type { Dictionaries, HunterSheet, InventoryItem, InventoryItemKind } from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { ResourceIcon } from '../../shared/ui/ResourceStock';
import { ExchangeDialog } from '../exchange/ExchangeDialog';
import { errorMessage, withHunter, type SheetActions } from './useCampaignSheet';

const KINDS: InventoryItemKind[] = ['EQUIPMENT', 'POTION', 'REWARD'];

interface InventoryPanelProps {
  campaignId: number;
  hunter: HunterSheet;
  hunters: HunterSheet[];
  dictionaries: Dictionaries;
  actions: SheetActions;
}

/**
 * Инвентарь охотника (`behavior.md` §7.3): снаряжение, зелья и карты наград. Правка без оплаты —
 * чтобы список совпадал с картами на столе; за ресурсы предметы создаются в кузнице и лаборатории.
 */
export function InventoryPanel({ campaignId, hunter, hunters, dictionaries, actions }: InventoryPanelProps) {
  const remove = useRemoveItem();
  const [editing, setEditing] = useState<InventoryItem | 'new' | null>(null);
  const [exchanging, setExchanging] = useState(false);

  const replaced = (next: HunterSheet) => actions.applied((sheet) => withHunter(sheet, hunter.id, () => next));
  const removeItem = (item: InventoryItem) => {
    remove.mutateAsync({ campaignId, hunterId: hunter.id, itemId: item.id }).then(replaced, actions.failed);
  };

  return (
    <Stack gap="xs" data-testid="inventory">
      <Group justify="space-between">
        <Title order={4}>{ru.inventory.title}</Title>
        <Group gap="xs">
          <Button size="xs" variant="light" onClick={() => setExchanging(true)} data-testid="inventory-exchange">
            {ru.inventory.exchange}
          </Button>
          <Button size="xs" variant="default" onClick={() => setEditing('new')} data-testid="inventory-add">
            {ru.inventory.add}
          </Button>
        </Group>
      </Group>
      {hunter.items.length === 0 && (
        <Text c="dimmed" size="sm" data-testid="inventory-empty">
          {ru.inventory.empty}
        </Text>
      )}
      {KINDS.map((kind) => {
        const items = hunter.items.filter((item) => item.kind === kind);
        if (items.length === 0) return null;
        return (
          <Stack key={kind} gap={4} data-testid="inventory-group" data-kind={kind}>
            <Text size="sm" fw={600} c="dimmed">
              {ru.inventory.kinds[kind]}
            </Text>
            {items.map((item) => (
              <Group key={item.id} gap="xs" wrap="nowrap" data-testid="inventory-item" data-name={item.name} data-kind={item.kind}>
                {item.element !== null && <ResourceIcon code={item.element} size={16} />}
                <Text size="sm" style={{ flex: 1, minWidth: 0 }}>
                  {item.name}
                </Text>
                {item.level !== null && (
                  <Badge variant="light" size="sm" data-testid="inventory-item-level" data-level={item.level}>
                    {ru.inventory.level(item.level)}
                  </Badge>
                )}
                <ActionIcon variant="subtle" onClick={() => setEditing(item)} aria-label={ru.inventory.edit(item.name)} data-testid="inventory-item-edit">
                  ✎
                </ActionIcon>
                <ActionIcon
                  variant="subtle"
                  color="red"
                  onClick={() => removeItem(item)}
                  aria-label={ru.inventory.remove(item.name)}
                  data-testid="inventory-item-remove"
                >
                  ×
                </ActionIcon>
              </Group>
            ))}
          </Stack>
        );
      })}
      <Text size="xs" c="dimmed">
        {ru.inventory.hint}
      </Text>
      <Modal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? ru.inventory.addTitle : ru.inventory.editTitle}
        centered
      >
        {editing !== null && (
          <ItemForm
            campaignId={campaignId}
            hunterId={hunter.id}
            item={editing === 'new' ? null : editing}
            onSaved={(next) => {
              replaced(next);
              setEditing(null);
            }}
            onCancel={() => setEditing(null)}
          />
        )}
      </Modal>
      <ExchangeDialog
        opened={exchanging}
        onClose={() => setExchanging(false)}
        campaignId={campaignId}
        hunters={hunters}
        hunterId={hunter.id}
        dictionaries={dictionaries}
        actions={actions}
      />
    </Stack>
  );
}

interface ItemFormProps {
  campaignId: number;
  hunterId: number;
  item: InventoryItem | null;
  onSaved: (hunter: HunterSheet) => void;
  onCancel: () => void;
}

function ItemForm({ campaignId, hunterId, item, onSaved, onCancel }: ItemFormProps) {
  const add = useAddItem();
  const edit = useEditItem();
  const [kind, setKind] = useState<InventoryItemKind>(item?.kind ?? 'EQUIPMENT');
  const [name, setName] = useState(item?.name ?? '');
  const [level, setLevel] = useState(item?.level ?? 1);
  const [error, setError] = useState<string | null>(null);
  const leveled = kind !== 'REWARD';

  const save = () => {
    setError(null);
    const data = { name: name.trim(), level: leveled ? level : null };
    const request =
      item === null
        ? add.mutateAsync({ campaignId, hunterId, data: { kind, ...data } })
        : edit.mutateAsync({ campaignId, hunterId, itemId: item.id, data });
    request.then(onSaved, (cause: unknown) => setError(errorMessage(cause)));
  };

  return (
    <Stack gap="sm" data-testid="inventory-form">
      {item === null && (
        <NativeSelect
          label={ru.inventory.kind}
          value={kind}
          data={KINDS.map((value) => ({ value, label: ru.inventory.kinds[value] ?? value }))}
          onChange={(event) => setKind(event.currentTarget.value as InventoryItemKind)}
          data-testid="inventory-form-kind"
        />
      )}
      <TextInput
        label={ru.inventory.name}
        value={name}
        maxLength={100}
        onChange={(event) => setName(event.currentTarget.value)}
        data-testid="inventory-form-name"
      />
      {leveled && (
        <NativeSelect
          label={ru.inventory.levelLabel}
          value={String(level)}
          data={['1', '2', '3']}
          onChange={(event) => setLevel(Number(event.currentTarget.value))}
          data-testid="inventory-form-level"
        />
      )}
      {error !== null && (
        <Alert color="red" data-testid="inventory-form-error">
          {error}
        </Alert>
      )}
      <Group justify="flex-end">
        <Button variant="default" onClick={onCancel}>
          {ru.inventory.cancel}
        </Button>
        <Button
          disabled={name.trim() === ''}
          loading={add.isPending || edit.isPending}
          onClick={save}
          data-testid="inventory-form-save"
        >
          {ru.inventory.save}
        </Button>
      </Group>
    </Stack>
  );
}
