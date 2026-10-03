import { Alert, Button, Group, Modal, NativeSelect, SegmentedControl, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useState } from 'react';
import { useConvertResources, useExchangeResources, useSellItem } from '../../api/generated/campaigns/campaigns';
import type {
  ConvertRequestGain,
  ConvertRequestSpendItem,
  Dictionaries,
  HunterSheet,
  SellRequestGain,
} from '../../api/generated/primal.schemas';
import { ru } from '../../shared/i18n/ru';
import { errorMessage, withHunter, type SheetActions } from '../campaign/useCampaignSheet';
import { kindsOf, type ResourceKind } from './exchange';

export type ExchangeMode = 'EXCHANGE' | 'CONVERT' | 'SELL';

/** Чего не хватает охотнику: код ресурса и сколько. */
export interface Lack {
  code: string;
  quantity: number;
}

interface ExchangeDialogProps {
  opened: boolean;
  onClose: () => void;
  campaignId: number;
  hunters: HunterSheet[];
  hunterId: number;
  dictionaries: Dictionaries;
  actions: SheetActions;
  /** Чего не хватает на предмет: подсказка и выбор по умолчанию. */
  lacking?: Lack[];
  /** Доступные варианты; лаборатории нужен только обмен — растения не преобразуются. */
  modes?: ExchangeMode[];
}

/**
 * «Обменять ресурсы» по правилам: обмен с другим охотником 1 к 1 внутри типа, преобразование (1 стихия или
 * 2 материи вместо 1 материи) и продажа карты снаряжения вместо материи или стихии её кузни.
 */
export function ExchangeDialog(props: ExchangeDialogProps) {
  return (
    <Modal opened={props.opened} onClose={props.onClose} title={ru.exchange.title} centered size="lg">
      {props.opened && <ExchangeContent {...props} />}
    </Modal>
  );
}

function ExchangeContent({
  onClose,
  campaignId,
  hunters,
  hunterId,
  dictionaries,
  actions,
  lacking = [],
  modes = ['EXCHANGE', 'CONVERT', 'SELL'],
}: ExchangeDialogProps) {
  const [mode, setMode] = useState<ExchangeMode>(modes[0] ?? 'EXCHANGE');
  const hunter = hunters.find((item) => item.id === hunterId);
  const names = new Map(
    [...dictionaries.elements, ...dictionaries.materials, ...dictionaries.plants].map((item) => [item.code, item.name]),
  );
  if (hunter === undefined) return null;

  /** Ответ описывает охотников полностью; обмен двумя списаниями может поднять версию дважды — лист перезапрашивается. */
  const done = (updated: HunterSheet[], message: string) => {
    actions.changed((sheet) => updated.reduce((current, next) => withHunter(current, next.id, () => next), sheet));
    notifications.show({ color: 'teal', message });
    onClose();
  };

  return (
    <Stack gap="md" data-testid="exchange-dialog">
      {lacking.length > 0 && (
        <Alert color="yellow" data-testid="exchange-lacking">
          {ru.exchange.lacking(hunter.playerName, lacking.map((unit) => `${names.get(unit.code) ?? unit.code} ${unit.quantity}`).join(', '))}
        </Alert>
      )}
      {modes.length > 1 && (
        <SegmentedControl
          fullWidth
          value={mode}
          onChange={(value) => setMode(value as ExchangeMode)}
          data={modes.map((value) => ({ value, label: ru.exchange.modes[value] }))}
          data-testid="exchange-mode"
        />
      )}
      {mode === 'EXCHANGE' && (
        <TradePanel
          campaignId={campaignId}
          hunter={hunter}
          others={hunters.filter((item) => item.id !== hunter.id)}
          dictionaries={dictionaries}
          names={names}
          lacking={lacking}
          onDone={done}
        />
      )}
      {mode === 'CONVERT' && (
        <ConvertPanel campaignId={campaignId} hunter={hunter} dictionaries={dictionaries} names={names} lacking={lacking} onDone={done} />
      )}
      {mode === 'SELL' && (
        <SellPanel campaignId={campaignId} hunter={hunter} dictionaries={dictionaries} names={names} lacking={lacking} onDone={done} />
      )}
    </Stack>
  );
}

interface PanelProps {
  campaignId: number;
  hunter: HunterSheet;
  dictionaries: Dictionaries;
  names: Map<string, string>;
  lacking: Lack[];
  onDone: (updated: HunterSheet[], message: string) => void;
}

/**
 * Обмен с другим охотником по правилам — 1 к 1 внутри типа: что хотите получить (один ресурс), что
 * предлагаете взамен (ресурс того же типа из своего запаса) и с кем (только охотники, у кого он есть).
 */
function TradePanel({ campaignId, hunter, others, dictionaries, names, lacking, onDone }: PanelProps & { others: HunterSheet[] }) {
  const exchange = useExchangeResources();
  const kinds = kindsOf(dictionaries);
  const groups: { kind: ResourceKind; codes: string[] }[] = [
    { kind: 'ELEMENT', codes: dictionaries.elements.map((item) => item.code) },
    { kind: 'MATERIAL', codes: dictionaries.materials.map((item) => item.code) },
    { kind: 'PLANT', codes: dictionaries.plants.map((item) => item.code) },
  ];
  const holders = (code: string) => others.filter((other) => (other.resources[code] ?? 0) > 0);
  // Из кузницы и лаборатории — недостающее, которое есть хоть у кого-то из отряда
  const [want, setWant] = useState(
    () => lacking.find((unit) => holders(unit.code).length > 0)?.code ?? lacking[0]?.code ?? '',
  );
  const [offer, setOffer] = useState('');
  const [partnerId, setPartnerId] = useState<number | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  const kind = kinds.get(want);
  const offers = groups
    .find((group) => group.kind === kind)
    ?.codes.filter((code) => code !== want && (hunter.resources[code] ?? 0) > 0) ?? [];
  const partners = want === '' ? [] : holders(want);
  const chosenOffer = offers.includes(offer) ? offer : (offers[0] ?? '');
  const partner = partners.find((other) => other.id === partnerId) ?? partners[0];

  if (others.length === 0) {
    return (
      <Text c="dimmed" data-testid="exchange-alone">
        {ru.exchange.alone}
      </Text>
    );
  }

  const submit = () => {
    if (partner === undefined) return;
    setError(null);
    exchange
      .mutateAsync({
        campaignId,
        data: { fromHunterId: hunter.id, toHunterId: partner.id, give: { [chosenOffer]: 1 }, receive: { [want]: 1 } },
      })
      .then(
        (result) =>
          onDone(
            result.hunters,
            ru.exchange.traded(hunter.playerName, partner.playerName, names.get(chosenOffer) ?? chosenOffer, names.get(want) ?? want),
          ),
        (cause: unknown) => setError(errorMessage(cause)),
      );
  };

  return (
    <Stack gap="sm" data-testid="exchange-trade">
      <Text size="sm" c="dimmed">
        {ru.exchange.tradeRule}
      </Text>
      <NativeSelect
        label={ru.exchange.want}
        value={want}
        data={[
          { value: '', label: ru.exchange.choose },
          ...groups.map((group) => ({
            group: ru.exchange.kinds[group.kind] ?? group.kind,
            items: group.codes.map((code) => ({ value: code, label: names.get(code) ?? code })),
          })),
        ]}
        onChange={(event) => setWant(event.currentTarget.value)}
        data-testid="exchange-want"
      />
      {want !== '' && (
        <>
          {offers.length === 0 ? (
            <Text size="sm" c="red" data-testid="exchange-no-offer">
              {ru.exchange.noOffer(hunter.playerName, ru.exchange.kinds[kind ?? ''] ?? '')}
            </Text>
          ) : (
            <NativeSelect
              label={ru.exchange.offer}
              value={chosenOffer}
              data={offers.map((code) => ({ value: code, label: `${names.get(code) ?? code} (${hunter.resources[code] ?? 0})` }))}
              onChange={(event) => setOffer(event.currentTarget.value)}
              data-testid="exchange-offer"
            />
          )}
          {partner === undefined ? (
            <Text size="sm" c="red" data-testid="exchange-no-partner">
              {ru.exchange.noPartner(names.get(want) ?? want)}
            </Text>
          ) : (
            <NativeSelect
              label={ru.exchange.partner}
              value={String(partner.id)}
              data={partners.map((other) => ({
                value: String(other.id),
                label: `${other.playerName} (${other.resources[want] ?? 0})`,
              }))}
              onChange={(event) => setPartnerId(Number(event.currentTarget.value))}
              data-testid="exchange-partner"
            />
          )}
        </>
      )}
      {error !== null && (
        <Alert color="red" data-testid="exchange-error">
          {error}
        </Alert>
      )}
      <Button
        disabled={want === '' || chosenOffer === '' || partner === undefined}
        loading={exchange.isPending}
        onClick={submit}
        data-testid="exchange-submit"
      >
        {ru.exchange.trade}
      </Button>
    </Stack>
  );
}

type ConvertKind = 'ELEMENT' | 'MATERIALS';

/** Преобразование: 1 стихия вместо 1 материи или 2 материи вместо 1. */
function ConvertPanel({ campaignId, hunter, dictionaries, names, lacking, onDone }: PanelProps) {
  const convert = useConvertResources();
  const elements = dictionaries.elements.map((item) => item.code).filter((code) => (hunter.resources[code] ?? 0) > 0);
  const materials = dictionaries.materials.map((item) => item.code);
  const ownedMaterials = materials.filter((code) => (hunter.resources[code] ?? 0) > 0);
  const lackingMaterial = lacking.find((unit) => materials.includes(unit.code))?.code;
  const [kind, setKind] = useState<ConvertKind>(elements.length > 0 ? 'ELEMENT' : 'MATERIALS');
  const [element, setElement] = useState(elements[0] ?? '');
  const [first, setFirst] = useState(ownedMaterials[0] ?? '');
  const [second, setSecond] = useState(ownedMaterials[1] ?? ownedMaterials[0] ?? '');
  const [gain, setGain] = useState(lackingMaterial ?? materials[0] ?? '');
  const [error, setError] = useState<string | null>(null);

  const spend = kind === 'ELEMENT' ? [element] : [first, second];
  const counts = new Map<string, number>();
  spend.forEach((code) => counts.set(code, (counts.get(code) ?? 0) + 1));
  const enough = spend.every((code) => code !== '') && [...counts].every(([code, count]) => (hunter.resources[code] ?? 0) >= count);
  const label = (code: string) => `${names.get(code) ?? code} (${hunter.resources[code] ?? 0})`;

  const submit = () => {
    setError(null);
    convert
      .mutateAsync({
        campaignId,
        hunterId: hunter.id,
        data: { spend: spend as ConvertRequestSpendItem[], gain: gain as ConvertRequestGain },
      })
      .then(
        (result) => onDone([result], ru.exchange.converted(spend.map((code) => names.get(code) ?? code).join(' + '), names.get(gain) ?? gain)),
        (cause: unknown) => setError(errorMessage(cause)),
      );
  };

  return (
    <Stack gap="sm" data-testid="exchange-convert">
      <SegmentedControl
        fullWidth
        value={kind}
        onChange={(value) => setKind(value as ConvertKind)}
        data={[
          { value: 'ELEMENT', label: ru.exchange.elementForMaterial },
          { value: 'MATERIALS', label: ru.exchange.twoForOne },
        ]}
        data-testid="exchange-convert-kind"
      />
      {kind === 'ELEMENT' ? (
        elements.length === 0 ? (
          <Text size="sm" c="dimmed">
            {ru.exchange.noElements}
          </Text>
        ) : (
          <NativeSelect label={ru.exchange.spendElement} value={element} data={elements.map((code) => ({ value: code, label: label(code) }))} onChange={(event) => setElement(event.currentTarget.value)} data-testid="exchange-spend-element" />
        )
      ) : (
        <Group grow>
          <NativeSelect label={ru.exchange.spendMaterial(1)} value={first} data={ownedMaterials.map((code) => ({ value: code, label: label(code) }))} onChange={(event) => setFirst(event.currentTarget.value)} data-testid="exchange-spend-first" />
          <NativeSelect label={ru.exchange.spendMaterial(2)} value={second} data={ownedMaterials.map((code) => ({ value: code, label: label(code) }))} onChange={(event) => setSecond(event.currentTarget.value)} data-testid="exchange-spend-second" />
        </Group>
      )}
      <NativeSelect label={ru.exchange.gain} value={gain} data={materials.map((code) => ({ value: code, label: label(code) }))} onChange={(event) => setGain(event.currentTarget.value)} data-testid="exchange-gain" />
      {!enough && (
        <Text size="sm" c="red" data-testid="exchange-not-enough">
          {ru.exchange.notEnough}
        </Text>
      )}
      {error !== null && (
        <Alert color="red" data-testid="exchange-error">
          {error}
        </Alert>
      )}
      <Button disabled={!enough} loading={convert.isPending} onClick={submit} data-testid="exchange-submit">
        {ru.exchange.convert}
      </Button>
    </Stack>
  );
}

/** Продажа: карта снаряжения или награды сбрасывается вместо 1 материи или 1 стихии её кузни. */
function SellPanel({ campaignId, hunter, dictionaries, names, lacking, onDone }: PanelProps) {
  const sell = useSellItem();
  const cards = hunter.items.filter((item) => item.kind !== 'POTION');
  const [itemId, setItemId] = useState(cards[0]?.id);
  const item = cards.find((card) => card.id === itemId);
  const options = [...dictionaries.materials.map((material) => material.code), ...(item?.element ? [item.element] : [])];
  const preferred = lacking.find((unit) => options.includes(unit.code))?.code;
  const [gain, setGain] = useState(preferred ?? options[0] ?? '');
  const [error, setError] = useState<string | null>(null);
  const chosen = options.includes(gain) ? gain : (options[0] ?? '');

  if (item === undefined) {
    return (
      <Text c="dimmed" data-testid="exchange-no-cards">
        {ru.exchange.noCards}
      </Text>
    );
  }

  const submit = () => {
    setError(null);
    sell
      .mutateAsync({ campaignId, hunterId: hunter.id, itemId: item.id, data: { gain: chosen as SellRequestGain } })
      .then(
        (result) => onDone([result], ru.exchange.sold(item.name, names.get(chosen) ?? chosen)),
        (cause: unknown) => setError(errorMessage(cause)),
      );
  };

  return (
    <Stack gap="sm" data-testid="exchange-sell">
      <Text size="sm" c="dimmed">
        {ru.exchange.sellRule}
      </Text>
      <NativeSelect
        label={ru.exchange.card}
        value={String(item.id)}
        data={cards.map((card) => ({ value: String(card.id), label: card.level === null ? card.name : `${card.name} · ${card.level}` }))}
        onChange={(event) => setItemId(Number(event.currentTarget.value))}
        data-testid="exchange-card"
      />
      <NativeSelect
        label={ru.exchange.gain}
        value={chosen}
        data={options.map((code) => ({ value: code, label: names.get(code) ?? code }))}
        onChange={(event) => setGain(event.currentTarget.value)}
        data-testid="exchange-gain"
      />
      {error !== null && (
        <Alert color="red" data-testid="exchange-error">
          {error}
        </Alert>
      )}
      <Button color="orange" loading={sell.isPending} onClick={submit} data-testid="exchange-submit">
        {ru.exchange.sell}
      </Button>
    </Stack>
  );
}
