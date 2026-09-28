package com.primal.realtime;

import com.primal.identity.PrimalPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Уведомления об изменении кампании для {@link EventHub}. Уходят только после коммита — откат ничего не
 * сообщает; несколько изменений одной кампании в транзакции дают одно событие. Автор — устройство запроса.
 */
@Component
public class ChangeEvents {

    /** Кампании, изменённые в текущей транзакции. */
    private static final Object PENDING = new Object();

    /** Что изменилось в кампании за транзакцию. */
    private static final class Change {
        boolean sheet;
        boolean battles;
        boolean access;
    }

    private final EventHub hub;

    ChangeEvents(EventHub hub) {
        this.hub = hub;
    }

    /** Лист кампании изменился — версия выросла (навыки, ресурсы, задания, итог боя, переход главы). */
    public void campaignChanged(long campaignId) {
        record(campaignId, change -> change.sheet = true);
    }

    /**
     * Изменились бои, а версия кампании — нет: отметка о начале, брошенный или отклонённый бой. Клиенты
     * перезапрашивают баннер идущих боёв и историю, даже если версия та же.
     */
    public void battlesChanged(long campaignId) {
        record(campaignId, change -> change.battles = true);
    }

    /** Доступ к кампании мог пропасть (кампанию удалили): поток проверит права подписчиков. */
    public void accessChanged(long campaignId) {
        record(campaignId, change -> change.access = true);
    }

    @SuppressWarnings("unchecked")
    private void record(long campaignId, Consumer<Change> mark) {
        UUID actor = currentDevice();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            Change change = new Change();
            mark.accept(change);
            deliver(Map.of(campaignId, change), actor);
            return;
        }
        Map<Long, Change> pending = (Map<Long, Change>) TransactionSynchronizationManager.getResource(PENDING);
        if (pending == null) {
            Map<Long, Change> changes = new LinkedHashMap<>();
            TransactionSynchronizationManager.bindResource(PENDING, changes);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(changes, actor);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(PENDING);
                }
            });
            pending = changes;
        }
        mark.accept(pending.computeIfAbsent(campaignId, key -> new Change()));
    }

    private void deliver(Map<Long, Change> changes, UUID actor) {
        changes.forEach((campaignId, change) -> {
            if (change.access) {
                hub.accessChanged(campaignId);
            } else {
                hub.campaignUpdated(campaignId, actor, change.battles);
            }
        });
    }

    private static UUID currentDevice() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof PrimalPrincipal principal
                ? principal.deviceId()
                : null;
    }
}
