import { Alert, Button, Group, Image, List, Loader, Modal, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { ApiError } from '../../api/errors';
import {
  createShareLink,
  getGetShareLinkQueryKey,
  revokeShareLink,
  useGetShareLink,
} from '../../api/generated/sharing/sharing';
import type { ShareLinkView } from '../../api/generated/primal.schemas';
import { copyText } from '../../shared/copyText';
import { ru } from '../../shared/i18n/ru';
import { errorMessage } from '../campaign/useCampaignSheet';
import { clockTime } from '../progression/campaignBattle';

type Confirm = 'REISSUE' | 'REVOKE' | null;

/** QR-код ссылки — раздать телефонам у стола. */
function useQrCode(url: string | undefined): string | null {
  const [image, setImage] = useState<{ url: string; data: string } | null>(null);
  useEffect(() => {
    if (url === undefined) return undefined;
    let active = true;
    QRCode.toDataURL(url, { margin: 1, width: 240 }).then(
      (data) => active && setImage({ url, data }),
      () => undefined,
    );
    return () => {
      active = false;
    };
  }, [url]);
  return image !== null && image.url === url ? image.data : null;
}

/**
 * «Поделиться» (`api.md` §9.1) — только владельцу: ссылка, «Копировать», QR-код, кто присоединился,
 * «Перевыпустить» и «Отозвать» с подтверждением.
 */
export function ShareDialog({ campaignId, opened, onClose }: { campaignId: number; opened: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const key = getGetShareLinkQueryKey(campaignId);
  const link = useGetShareLink(campaignId, { query: { enabled: opened, retry: false } });
  const missing = link.error instanceof ApiError && link.error.status === 404;
  const view = missing ? undefined : link.data;
  const qr = useQrCode(view?.url);
  const [confirm, setConfirm] = useState<Confirm>(null);
  const [busy, setBusy] = useState(false);
  const [copiedUrl, setCopiedUrl] = useState<string | null>(null);

  const run = async (action: () => Promise<ShareLinkView | undefined>) => {
    setBusy(true);
    try {
      const next = await action();
      if (next === undefined) queryClient.removeQueries({ queryKey: key });
      else queryClient.setQueryData(key, next);
      void queryClient.invalidateQueries({ queryKey: key });
    } catch (error) {
      notifications.show({ color: 'red', message: errorMessage(error) });
    } finally {
      setBusy(false);
      setConfirm(null);
    }
  };

  // По HTTP с адреса в сети буфера обмена нет — copyText копирует старым способом или сообщает о неудаче
  const copy = async (url: string) => {
    const copied = await copyText(url);
    setCopiedUrl(copied ? url : null);
    notifications.show(
      copied
        ? { id: 'share-copied', color: 'teal', message: ru.sharing.copied }
        : { id: 'share-copy-failed', color: 'yellow', message: ru.sharing.copyFailed },
    );
  };

  const create = () => run(() => createShareLink(campaignId));
  const revoke = () => run(async () => {
    await revokeShareLink(campaignId);
    return undefined;
  });

  return (
    <Modal opened={opened} onClose={onClose} title={ru.sharing.title} centered>
      <Stack gap="md" data-testid="share-dialog">
        <Text size="sm" c="dimmed">
          {ru.sharing.intro}
        </Text>
        {link.isPending && !missing && <Loader size="sm" />}
        {link.isError && !missing && <Alert color="red">{errorMessage(link.error)}</Alert>}

        {(missing || (link.isSuccess && view === undefined)) && (
          <Stack gap="xs" align="flex-start">
            <Text data-testid="share-no-link">{ru.sharing.noLink}</Text>
            <Button loading={busy} onClick={() => void create()} data-testid="share-create">
              {ru.sharing.create}
            </Button>
          </Stack>
        )}

        {view !== undefined && (
          <>
            <Group gap="xs" align="flex-end" wrap="nowrap">
              <TextInput
                label={ru.sharing.link}
                value={view.url}
                readOnly
                style={{ flex: 1 }}
                onFocus={(event) => event.currentTarget.select()}
                data-testid="share-url"
              />
              <Button
                variant={copiedUrl === view.url ? 'filled' : 'light'}
                color={copiedUrl === view.url ? 'teal' : undefined}
                onClick={() => void copy(view.url)}
                data-testid="share-copy"
              >
                {ru.sharing.copy}
              </Button>
            </Group>
            {qr !== null && (
              <Group justify="center">
                <Image src={qr} alt={ru.sharing.qrAlt} w={200} h={200} data-testid="share-qr" />
              </Group>
            )}
            <Stack gap={4}>
              <Text fw={600}>{ru.sharing.joined}</Text>
              {view.joined.length === 0 ? (
                <Text size="sm" c="dimmed" data-testid="share-nobody">
                  {ru.sharing.nobody}
                </Text>
              ) : (
                <List size="sm" spacing={2}>
                  {view.joined.map((participant) => (
                    <List.Item key={`${participant.kind}-${participant.name}-${participant.joinedAt}`} data-testid="share-participant">
                      {`${participant.name} (${ru.sharing.kinds[participant.kind]}), ${clockTime(participant.joinedAt)}`}
                    </List.Item>
                  ))}
                </List>
              )}
            </Stack>
            <Group justify="space-between">
              <Button variant="light" onClick={() => setConfirm('REISSUE')} disabled={busy} data-testid="share-reissue">
                {ru.sharing.reissue}
              </Button>
              <Button variant="subtle" color="red" onClick={() => setConfirm('REVOKE')} disabled={busy} data-testid="share-revoke">
                {ru.sharing.revoke}
              </Button>
            </Group>
          </>
        )}

        {confirm !== null && (
          <Alert color="orange" title={confirm === 'REISSUE' ? ru.sharing.reissueTitle : ru.sharing.revokeTitle} data-testid="share-confirm">
            <Stack gap="xs">
              <Text size="sm">{confirm === 'REISSUE' ? ru.sharing.reissueText : ru.sharing.revokeText}</Text>
              <Group justify="flex-end">
                <Button size="xs" variant="default" onClick={() => setConfirm(null)} data-testid="share-confirm-cancel">
                  {ru.sharing.cancel}
                </Button>
                <Button
                  size="xs"
                  color="red"
                  loading={busy}
                  onClick={() => void (confirm === 'REISSUE' ? create() : revoke())}
                  data-testid="share-confirm-ok"
                >
                  {confirm === 'REISSUE' ? ru.sharing.reissue : ru.sharing.revoke}
                </Button>
              </Group>
            </Stack>
          </Alert>
        )}
      </Stack>
    </Modal>
  );
}
