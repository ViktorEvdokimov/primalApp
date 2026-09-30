package com.primal.campaign;

import static com.primal.support.Xsrf.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.primal.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("Достижения кампании вручную")
class AchievementIT extends IntegrationTest {

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
        jdbc.update("update campaign set chapter = 7 where id = ?", campaignId);
    }

    @AfterEach
    void cleanUp() {
        deleteIdentityData();
    }

    private ResultActions add(String name) throws Exception {
        return mockMvc.perform(post("/api/v1/campaigns/" + campaignId + "/achievements").with(xsrf()).cookie(alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + name + "\"}"));
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from campaign_achievement where campaign_id = ?", Integer.class, campaignId);
    }

    @Test
    @DisplayName("«голос  волтьяра» сохраняется как «Голос Волтьяра» с кодом GOLOS_VOLTYARA")
    void matchesCatalog() throws Exception {
        // вызов и проверка
        add("голос  волтьяра")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("GOLOS_VOLTYARA"))
                .andExpect(jsonPath("$.name").value("Голос Волтьяра"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.grantedInChapter").value(7))
                .andExpect(jsonPath("$.matchedCatalog").value(true));
    }

    @Test
    @DisplayName("второй раз то же достижение не добавляется")
    void noDuplicates() throws Exception {
        // подготовка
        add("Голос Волтьяра").andExpect(status().isCreated());

        // вызов
        add("ГОЛОС ВОЛТЬЯРА").andExpect(status().isCreated());

        // проверка
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("своё достижение: другое написание заменяет прежнее")
    void customReplaced() throws Exception {
        // подготовка
        add("Моё достижение").andExpect(jsonPath("$.code").isEmpty()).andExpect(jsonPath("$.matchedCatalog").value(false));

        // вызов
        add("моё   Достижение").andExpect(status().isCreated());

        // проверка
        assertThat(jdbc.queryForList("select name from campaign_achievement where campaign_id = ?", String.class, campaignId))
                .containsExactly("моё Достижение");
    }

    @Test
    @DisplayName("удаление; чужое или несуществующее достижение → 404; пустое название → 400")
    void deleteAndErrors() throws Exception {
        // подготовка
        long id = ((Number) JsonPath.read(add("Затишье").andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        // вызов и проверка
        mockMvc.perform(delete("/api/v1/campaigns/" + campaignId + "/achievements/" + id).with(xsrf()).cookie(alice))
                .andExpect(status().isNoContent());
        assertThat(count()).isZero();
        mockMvc.perform(delete("/api/v1/campaigns/" + campaignId + "/achievements/" + id).with(xsrf()).cookie(alice))
                .andExpect(status().isNotFound());
        add("  ").andExpect(status().isBadRequest());
    }
}
