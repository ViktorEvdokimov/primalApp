package com.primal.realtime;

import com.primal.access.AccessService;
import com.primal.access.ShareLinkChanged;
import com.primal.common.error.ApiException;
import com.primal.identity.AccountService;
import com.primal.identity.AccountService.Author;
import com.primal.identity.DevicesRevoked;
import com.primal.identity.PrimalPrincipal;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Подписчики потоков {@code /campaigns/{id}/events} в памяти ({@code doc/api.md} §9.3): один экземпляр бэкенда
 * (architecture.md §8). События только уведомляют — данные клиент перезапрашивает обычными запросами, поэтому
 * права проверяются при подписке и при изменении доступа.
 */
@Component
public class EventHub {

    private static final Logger log = LoggerFactory.getLogger(EventHub.class);

    /**
     * Кто и что изменил: версия кампании после коммита и автор изменения. {@code battles} — изменились бои
     * (отметки о начале, история): клиенту перезапрашивать, даже если версия не выросла.
     */
    public record CampaignUpdated(long campaignId, int version, boolean battles, EventActor actor) {
    }

    /** Автор изменения; {@code null} — изменение без запроса пользователя. */
    public record EventActor(String kind, String name) {
    }

    private record Subscriber(SseEmitter emitter, PrimalPrincipal principal) {
    }

    private final Map<Long, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final AccessService access;
    private final AccountService accounts;
    private final JdbcTemplate jdbc;

    EventHub(AccessService access, AccountService accounts, JdbcTemplate jdbc) {
        this.access = access;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    /** Подписка: права уже проверены. Поток без таймаута, закрывается клиентом или при потере доступа. */
    SseEmitter subscribe(long campaignId, PrimalPrincipal principal) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(emitter, principal);
        Set<Subscriber> campaign = subscribers.computeIfAbsent(campaignId, key -> ConcurrentHashMap.newKeySet());
        campaign.add(subscriber);
        Runnable remove = () -> campaign.remove(subscriber);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        // Первая строка сразу: прокси и браузер видят, что поток открыт
        send(campaignId, subscriber, SseEmitter.event().comment("connected"));
        return emitter;
    }

    /** Кампания изменилась (после коммита): подписчики получают новую версию и автора. */
    void campaignUpdated(long campaignId, UUID actorDevice, boolean battles) {
        Set<Subscriber> campaign = subscribers.get(campaignId);
        if (campaign == null || campaign.isEmpty()) {
            return;
        }
        List<Integer> versions = jdbc.queryForList("select version from campaign where id = ?", Integer.class, campaignId);
        if (versions.isEmpty()) {
            accessChanged(campaignId);
            return;
        }
        EventActor actor = null;
        if (actorDevice != null) {
            Author author = accounts.author(actorDevice);
            actor = new EventActor(author.guest() ? "GUEST" : "USER", author.name());
        }
        CampaignUpdated update = new CampaignUpdated(campaignId, versions.getFirst(), battles, actor);
        campaign.forEach(subscriber ->
                send(campaignId, subscriber, SseEmitter.event().name("campaign.updated").data(update, MediaType.APPLICATION_JSON)));
    }

    /** Доступ к кампании мог пропасть (отзыв ссылки, удаление кампании): у кого его нет — {@code access.revoked}. */
    void accessChanged(long campaignId) {
        Set<Subscriber> campaign = subscribers.get(campaignId);
        if (campaign == null) {
            return;
        }
        campaign.forEach(subscriber -> {
            if (!hasAccess(subscriber.principal(), campaignId)) {
                revoke(campaignId, subscriber);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onShareLinkChanged(ShareLinkChanged event) {
        accessChanged(event.campaignId());
    }

    /** Отозванные устройства теряют все потоки. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onDevicesRevoked(DevicesRevoked event) {
        Set<UUID> revoked = Set.copyOf(event.deviceIds());
        subscribers.forEach((campaignId, campaign) -> campaign.forEach(subscriber -> {
            if (revoked.contains(subscriber.principal().deviceId())) {
                revoke(campaignId, subscriber);
            }
        }));
    }

    /** Пульс каждые 25 секунд: прокси не закрывают молчащее соединение, а оборванные потоки отпадают. */
    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    void heartbeat() {
        subscribers.forEach((campaignId, campaign) ->
                campaign.forEach(subscriber -> send(campaignId, subscriber, SseEmitter.event().comment("ping"))));
    }

    /** Число подписчиков кампании — для тестов. */
    int subscribers(long campaignId) {
        Set<Subscriber> campaign = subscribers.get(campaignId);
        return campaign == null ? 0 : campaign.size();
    }

    private void revoke(long campaignId, Subscriber subscriber) {
        send(campaignId, subscriber, SseEmitter.event().name("access.revoked").data(Map.of(), MediaType.APPLICATION_JSON));
        subscribers.getOrDefault(campaignId, Set.of()).remove(subscriber);
        subscriber.emitter().complete();
    }

    private boolean hasAccess(PrimalPrincipal principal, long campaignId) {
        try {
            access.require(principal, campaignId);
            return true;
        } catch (ApiException denied) {
            return false;
        }
    }

    private void send(long campaignId, Subscriber subscriber, SseEmitter.SseEventBuilder event) {
        try {
            subscriber.emitter().send(event);
        } catch (IOException | IllegalStateException gone) {
            // Клиент ушёл: поток больше не нужен
            log.debug("Поток событий кампании {} закрыт: {}", campaignId, gone.getMessage());
            subscribers.getOrDefault(campaignId, Set.of()).remove(subscriber);
            subscriber.emitter().completeWithError(gone);
        }
    }
}
