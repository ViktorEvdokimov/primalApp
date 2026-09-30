package com.primal.realtime;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.identity.DeviceCookies;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("Поток изменений кампании")
class CampaignEventsIT extends IntegrationTest {

    @Autowired
    private ChangeEvents changes;

    @Autowired
    private EventHub hub;

    @Autowired
    private TransactionTemplate transactions;

    private Cookie alice;
    private long campaignId;

    @BeforeEach
    void createCampaign() throws Exception {
        alice = auth.login("alice");
        String body = mockMvc.perform(post("/api/v1/campaigns").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Кампания\", \"hunters\": [{\"class\": \"DAREON\"}, {\"class\": \"MIRA\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        campaignId = ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    /** Подписка на поток; содержимое ответа растёт по мере событий. */
    private MvcResult subscribe(Cookie cookie) throws Exception {
        return mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/events").cookie(cookie)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static String stream(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private int version() {
        return jdbc.queryForObject("select version from campaign where id = ?", Integer.class, campaignId);
    }

    @Test
    @DisplayName("правка листа после коммита — campaign.updated с новой версией и автором")
    void sheetEdit() throws Exception {
        // подготовка
        MvcResult events = subscribe(alice);
        assertThat(stream(events)).contains(":connected");

        // вызов
        mockMvc.perform(patch("/api/v1/campaigns/" + campaignId).with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": " + version() + ", \"notes\": \"Заметки\"}"))
                .andExpect(status().isOk());

        // проверка
        assertThat(stream(events))
                .contains("event:campaign.updated")
                .contains("\"version\":" + version())
                .contains("\"battles\":false")
                .contains("\"actor\":{\"kind\":\"USER\",\"name\":\"alice\"}");
    }

    @Test
    @DisplayName("несколько изменений в транзакции — одно событие; откат — ни одного")
    void afterCommitOnly() throws Exception {
        // подготовка
        MvcResult events = subscribe(alice);

        // вызов: откат
        transactions.executeWithoutResult(status -> {
            changes.campaignChanged(campaignId);
            status.setRollbackOnly();
        });

        // проверка
        assertThat(stream(events)).doesNotContain("campaign.updated");

        // вызов: коммит двух изменений
        transactions.executeWithoutResult(status -> {
            changes.campaignChanged(campaignId);
            changes.campaignChanged(campaignId);
        });

        // проверка
        assertThat(stream(events).split("event:campaign.updated", -1)).hasSize(2);
    }

    @Test
    @DisplayName("отметка о начале боя и принятый результат уведомляют участников")
    void battleMarkAndResult() throws Exception {
        // подготовка
        MvcResult events = subscribe(alice);
        UUID battle = UUID.randomUUID();

        // вызов: отметка о начале
        mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/battles").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "questNumber": null, "bossCode": "VIRAXEN", "difficulty": 0, "chapter": 0,
                                 "progressSeq": 0, "startedAt": "%s"}""".formatted(battle, clock.instant())))
                .andExpect(status().isCreated());

        // проверка: версия та же, но изменились бои — клиентам перезапрашивать
        assertThat(stream(events).split("event:campaign.updated", -1)).hasSize(2);
        assertThat(stream(events)).contains("\"battles\":true");

        // вызов: принятый результат
        mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/battles/" + battle + "/result").with(xsrf()).cookie(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questNumber": null, "bossCode": "VIRAXEN", "difficulty": 0, "chapter": 0, "progressSeq": 0,
                                 "result": "VICTORY", "roundsPlayed": 3, "startedAt": "%s", "finishedAt": "%s",
                                 "action": "ACCEPT"}""".formatted(clock.instant(), clock.instant())))
                .andExpect(status().isOk());

        // проверка
        assertThat(stream(events).split("event:campaign.updated", -1)).hasSize(3);
        assertThat(stream(events)).contains("\"version\":" + version());
    }

    @Test
    @DisplayName("отзыв ссылки — access.revoked и закрытие потока гостя; владелец остаётся подписан")
    void linkRevoked() throws Exception {
        // подготовка
        String link = mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/share-link").with(xsrf()).cookie(alice))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(link, "$.url");
        Cookie guest = mockMvc.perform(post("/api/v1/share/" + url.substring(url.lastIndexOf('/') + 1) + "/join").with(xsrf()))
                .andReturn().getResponse().getCookie(DeviceCookies.NAME);
        MvcResult guestEvents = subscribe(guest);
        MvcResult ownerEvents = subscribe(alice);
        assertThat(hub.subscribers(campaignId)).isEqualTo(2);

        // вызов
        mockMvc.perform(delete("/api/v1/campaigns/" + campaignId + "/share-link").with(xsrf()).cookie(alice))
                .andExpect(status().isNoContent());

        // проверка
        assertThat(stream(guestEvents)).contains("event:access.revoked");
        assertThat(stream(ownerEvents)).doesNotContain("access.revoked");
        assertThat(hub.subscribers(campaignId)).isEqualTo(1);
    }

    @Test
    @DisplayName("выход с устройства закрывает его потоки; подписка без прав → 404")
    void deviceRevokedAndNoAccess() throws Exception {
        // подготовка
        MvcResult events = subscribe(alice);
        Cookie bob = auth.login("bob");

        // вызов и проверка
        mockMvc.perform(get("/api/v1/campaigns/" + campaignId + "/events").cookie(bob).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/auth/logout").with(xsrf()).cookie(alice)).andExpect(status().isNoContent());
        assertThat(stream(events)).contains("event:access.revoked");
        assertThat(hub.subscribers(campaignId)).isZero();
    }
}
