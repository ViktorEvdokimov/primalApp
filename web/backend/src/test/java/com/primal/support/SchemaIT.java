package com.primal.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("Схема БД (миграция V1)")
@Transactional
class SchemaIT extends IntegrationTest {

    @Autowired
    private Flyway flyway;

    @Nested
    @DisplayName("Миграция")
    class Migration {

        @Test
        @DisplayName("создаёт 19 таблиц из doc/data-model.md")
        void createsAllTables() {
            // вызов
            Integer tables = jdbc.queryForObject("""
                    select count(*) from information_schema.tables
                    where table_schema = 'public' and table_name <> 'flyway_schema_history'""", Integer.class);

            // проверка
            assertThat(tables).isEqualTo(19);
        }

        @Test
        @DisplayName("повторный запуск ничего не меняет")
        void secondRunDoesNothing() {
            // вызов
            int executed = flyway.migrate().migrationsExecuted;

            // проверка
            assertThat(executed).isZero();
        }
    }

    @Nested
    @DisplayName("Ограничения")
    class Constraints {

        private long campaignId;

        @BeforeEach
        void campaign() {
            // подготовка: пользователь и кампания; босс TORAMAT уже есть — каталог заполняет миграция R__Catalog
            long userId = jdbc.queryForObject(
                    "insert into app_user (email) values ('alice@example.com') returning id", Long.class);
            campaignId = jdbc.queryForObject(
                    "insert into campaign (owner_id, name) values (?, 'Тест') returning id", Long.class, userId);
        }

        @Test
        @DisplayName("в отряде не больше 5 охотников")
        void noSixthHunter() {
            // подготовка
            String[] classes = {"DAREON", "MIRA", "TOREG", "LIONAR", "KARA"};
            for (int i = 0; i < classes.length; i++) {
                jdbc.update("insert into campaign_hunter (campaign_id, hunter_class, player_name, position) values (?, ?, ?, ?)",
                        campaignId, classes[i], classes[i], i + 1);
            }

            // вызов и проверка
            assertThatThrownBy(() -> jdbc.update(
                    "insert into campaign_hunter (campaign_id, hunter_class, player_name, position) values (?, 'DRUSK', 'Друск', 6)",
                    campaignId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("campaign_hunter_position_check");
        }

        @Test
        @DisplayName("трофей за бой выдаётся один раз")
        void oneTrophyPerBattle() {
            // подготовка
            UUID battleId = battle("APPLIED", "VICTORY");
            jdbc.update("insert into campaign_trophy (campaign_id, boss_code, chapter, campaign_battle_id) values (?, 'TORAMAT', 2, ?)",
                    campaignId, battleId);

            // вызов и проверка
            assertThatThrownBy(() -> jdbc.update(
                    "insert into campaign_trophy (campaign_id, boss_code, chapter, campaign_battle_id) values (?, 'TORAMAT', 2, ?)",
                    campaignId, battleId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("campaign_trophy_campaign_battle_id_key");
        }

        @Test
        @DisplayName("у кампании одна действующая ссылка")
        void oneActiveShareLink() {
            // подготовка
            long ownerId = jdbc.queryForObject("select owner_id from campaign where id = ?", Long.class, campaignId);
            jdbc.update("insert into share_link (id, campaign_id, created_by) values (?, ?, ?)", UUID.randomUUID(), campaignId, ownerId);

            // вызов и проверка
            assertThatThrownBy(() -> jdbc.update(
                    "insert into share_link (id, campaign_id, created_by) values (?, ?, ?)", UUID.randomUUID(), campaignId, ownerId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ux_share_link_campaign");
        }

        @Test
        @DisplayName("ресурс не уходит в минус даже при атомарном начислении")
        void resourceNeverNegative() {
            // подготовка
            long hunterId = jdbc.queryForObject("""
                    insert into campaign_hunter (campaign_id, hunter_class, player_name, position)
                    values (?, 'DAREON', 'Алиса', 1) returning id""", Long.class, campaignId);
            jdbc.update("insert into hunter_resource (hunter_id, resource, quantity) values (?, 'BONES', 1)", hunterId);

            // вызов и проверка
            assertThatThrownBy(() -> jdbc.update("""
                    insert into hunter_resource (hunter_id, resource, quantity) values (?, 'BONES', -2)
                    on conflict (hunter_id, resource) do update set quantity = hunter_resource.quantity + excluded.quantity""",
                    hunterId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("hunter_resource_quantity_check");
        }

        @Test
        @DisplayName("принятый бой обязан иметь исход, идущий — не может")
        void appliedBattleNeedsResult() {
            // вызов и проверка
            assertThatThrownBy(() -> battle("APPLIED", null))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("campaign_battle_check");
        }

        @Test
        @DisplayName("два идущих боя одной кампании допускаются — это только предупреждение")
        void parallelBattlesAllowed() {
            // вызов
            battle("IN_PROGRESS", null);
            battle("IN_PROGRESS", null);

            // проверка
            Integer inProgress = jdbc.queryForObject(
                    "select count(*) from campaign_battle where campaign_id = ? and status = 'IN_PROGRESS'", Integer.class, campaignId);
            assertThat(inProgress).isEqualTo(2);
        }

        private UUID battle(String status, String result) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    insert into campaign_battle (id, campaign_id, purpose, difficulty, chapter, progress_seq, status, result, started_at)
                    values (?, ?, 'FREE', 1, 2, 0, ?, ?, now())""", id, campaignId, status, result);
            return id;
        }
    }
}
