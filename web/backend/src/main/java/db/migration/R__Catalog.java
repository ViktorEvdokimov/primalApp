package db.migration;

import com.primal.catalog.AchievementDef;
import com.primal.catalog.BossDef;
import com.primal.catalog.Catalog;
import com.primal.catalog.CatalogLoader;
import com.primal.catalog.ChapterDef;
import com.primal.catalog.QuestDef;
import com.primal.catalog.StanceDef;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Каталог из YAML ({@code classpath:catalog/}) → таблицы каталога (doc/data-model.md §4.1).
 * Repeatable-миграция: Flyway повторяет её, когда меняется контрольная сумма файлов каталога, поэтому
 * исправление данных — это правка YAML без новой миграции. Записи обновляются (upsert), стойки босса
 * перезаписываются целиком. Удалённые из YAML боссы и достижения в БД остаются: на них могут ссылаться кампании.
 */
public class R__Catalog extends BaseJavaMigration {

    private final Catalog catalog = CatalogLoader.load();

    @Override
    public Integer getChecksum() {
        return catalog.checksum().hashCode();
    }

    @Override
    public String getDescription() {
        return "Catalog";
    }

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        upsertBosses(connection, catalog.bosses());
        upsertAchievements(connection, catalog.achievements());
        upsertQuests(connection, catalog.quests());
        upsertChapters(connection, catalog.chapters());
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into catalog_version (id, checksum, loaded_at) values (true, ?, now())
                on conflict (id) do update set checksum = excluded.checksum, loaded_at = excluded.loaded_at""")) {
            statement.setString(1, catalog.checksum());
            statement.executeUpdate();
        }
    }

    private static void upsertBosses(Connection connection, List<BossDef> bosses) throws SQLException {
        try (PreparedStatement boss = connection.prepareStatement("""
                     insert into boss (code, name, element, expansion, sort_order) values (?, ?, ?, ?, ?)
                     on conflict (code) do update set name = excluded.name, element = excluded.element,
                         expansion = excluded.expansion, sort_order = excluded.sort_order""");
             PreparedStatement deleteStances = connection.prepareStatement("delete from boss_stance where boss_code = ?");
             PreparedStatement stance = connection.prepareStatement("""
                     insert into boss_stance (boss_code, difficulty, stance_no, toughness_per_hunter, change_mode, change_at_health)
                     values (?, ?, ?, ?, ?, ?)""")) {
            for (BossDef def : bosses) {
                boss.setString(1, def.code());
                boss.setString(2, def.name());
                setText(boss, 3, def.element() == null ? null : def.element().name());
                setText(boss, 4, def.expansion() == null ? null : def.expansion().name());
                boss.setInt(5, def.sortOrder());
                boss.executeUpdate();

                deleteStances.setString(1, def.code());
                deleteStances.executeUpdate();
                for (Map.Entry<Integer, List<StanceDef>> level : def.stances().entrySet()) {
                    for (StanceDef s : level.getValue()) {
                        stance.setString(1, def.code());
                        stance.setInt(2, level.getKey());
                        stance.setInt(3, s.stance());
                        setInt(stance, 4, s.toughnessPerHunter());
                        stance.setString(5, s.changeMode().name());
                        setInt(stance, 6, s.changeAtHealth());
                        stance.addBatch();
                    }
                }
            }
            stance.executeBatch();
        }
    }

    private static void upsertAchievements(Connection connection, List<AchievementDef> achievements) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into achievement_def (code, name) values (?, ?)
                on conflict (code) do update set name = excluded.name""")) {
            for (AchievementDef achievement : achievements) {
                statement.setString(1, achievement.code());
                statement.setString(2, achievement.name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void upsertQuests(Connection connection, List<QuestDef> quests) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into quest_def (number, name, boss_code, expansion, victory_effects, defeat_effects)
                values (?, ?, ?, ?, ?::jsonb, ?::jsonb)
                on conflict (number) do update set name = excluded.name, boss_code = excluded.boss_code,
                    expansion = excluded.expansion, victory_effects = excluded.victory_effects,
                    defeat_effects = excluded.defeat_effects""")) {
            for (QuestDef quest : quests) {
                statement.setInt(1, quest.number());
                statement.setString(2, quest.name());
                statement.setString(3, quest.bossCode());
                setText(statement, 4, quest.expansion() == null ? null : quest.expansion().name());
                statement.setString(5, quest.victoryJson());
                statement.setString(6, quest.defeatJson());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void upsertChapters(Connection connection, List<ChapterDef> chapters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into chapter_def (chapter, effects, decisions) values (?, ?::jsonb, ?::jsonb)
                on conflict (chapter) do update set effects = excluded.effects, decisions = excluded.decisions""")) {
            for (ChapterDef chapter : chapters) {
                statement.setInt(1, chapter.chapter());
                statement.setString(2, chapter.effectsJson());
                statement.setString(3, chapter.decisionsJson());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void setText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.SMALLINT);
        } else {
            statement.setInt(index, value);
        }
    }
}
