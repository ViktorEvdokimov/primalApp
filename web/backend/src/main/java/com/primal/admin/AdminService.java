package com.primal.admin;

import com.primal.catalog.BossDef;
import com.primal.catalog.CatalogDtos.Boss;
import com.primal.catalog.CatalogDtos.ForgeCost;
import com.primal.catalog.CatalogDtos.ForgeItem;
import com.primal.catalog.CatalogDtos.LabPotion;
import com.primal.catalog.CatalogDtos.LabUnit;
import com.primal.catalog.CatalogDtos.Stance;
import com.primal.catalog.CatalogEditor;
import com.primal.catalog.CatalogEditor.Edited;
import com.primal.catalog.CatalogService;
import com.primal.catalog.ChapterDef;
import com.primal.catalog.ForgeItemDef;
import com.primal.catalog.LabPotionDef;
import com.primal.catalog.QuestDef;
import com.primal.common.api.ApiNullable;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.Admins;
import com.primal.identity.PrimalPrincipal;
import com.primal.rules.effects.Effect;
import com.primal.rules.effects.EffectDescriber;
import com.primal.rules.effects.EffectDescriber.Context;
import com.primal.rules.model.Element;
import com.primal.rules.model.ForgeSlot;
import com.primal.rules.model.HunterClass;
import com.primal.rules.model.Plant;
import com.primal.rules.model.StanceChangeMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Правка каталога администратором: проверка роли, затем {@link CatalogEditor}. */
@Service
class AdminService {

    /**
     * Задание для редактора: награды за победу и последствия невыполненного задания на языке каталога и их
     * формулировки для игроков; {@code edited} — награды изменены администратором (не из YAML).
     */
    record AdminQuest(int number, String name, String bossCode, String bossName, @ApiNullable String expansion,
                      List<Map<String, Object>> victory, List<Map<String, Object>> expired,
                      List<String> victoryText, List<String> expiredText, boolean edited) {
    }

    /** Глава для редактора: эффекты главы (решения главы не редактируются). */
    record AdminChapter(int chapter, List<Map<String, Object>> effects, List<String> text, boolean edited) {
    }

    /** Предмет кузни: материи по уровням 1–3 (кроме них — 1 стихия кузни). */
    record AdminForgeItem(String code, Element element, String name, ForgeSlot slot, @ApiNullable HunterClass hunterClass,
                          List<ForgeCost> costs, boolean edited) {
    }

    /** Зелье лаборатории: растения по одному. */
    record AdminLabPotion(String code, String name, List<LabUnit> units, boolean edited) {
    }

    /** Босс: стойки по уровням враждебности — как в справочнике боссов. */
    record AdminBoss(String code, String name, @ApiNullable String element, @ApiNullable String expansion,
                     Map<String, List<Stance>> difficulties, boolean edited) {
    }

    /** Стойка в правке: прочность за охотника ({@code null} — нет порога раны) и когда стойка меняется. */
    record StanceInput(@ApiNullable Integer toughnessPerHunter, StanceChangeMode mode, @ApiNullable Integer atHealth) {
    }

    record AdminCatalog(List<AdminQuest> quests, List<AdminChapter> chapters, List<AdminForgeItem> forge,
                        List<AdminLabPotion> lab, List<AdminBoss> bosses) {
    }

    private static final JsonMapper JSON = new JsonMapper();
    /** Одной материи на уровне цены — не больше, чем всего материй на уровне при правке. */
    private static final int UNIT_QUANTITY_MAX = 4;

    private final Admins admins;
    private final CatalogService catalog;
    private final CatalogEditor editor;

    AdminService(Admins admins, CatalogService catalog, CatalogEditor editor) {
        this.admins = admins;
        this.catalog = catalog;
        this.editor = editor;
    }

    AdminCatalog catalog(PrimalPrincipal principal) {
        admins.require(principal);
        Edited edited = editor.edited();
        return new AdminCatalog(
                catalog.quests().stream().map(def -> quest(def, edited)).toList(),
                catalog.chapters().stream().map(def -> chapter(def, edited)).toList(),
                catalog.forge().stream().map(def -> forge(def, edited)).toList(),
                catalog.lab().stream().map(def -> lab(def, edited)).toList(),
                catalog.bosses().stream().map(def -> boss(def, edited)).toList());
    }

    AdminQuest editQuest(PrimalPrincipal principal, int number, List<Map<String, Object>> victory,
                         List<Map<String, Object>> expired) {
        long adminId = admins.require(principal);
        return quest(editor.editQuest(number, JSON.valueToTree(victory), JSON.valueToTree(expired), adminId), editor.edited());
    }

    AdminQuest resetQuest(PrimalPrincipal principal, int number) {
        admins.require(principal);
        return quest(editor.resetQuest(number), editor.edited());
    }

    AdminChapter editChapter(PrimalPrincipal principal, int number, List<Map<String, Object>> effects) {
        long adminId = admins.require(principal);
        return chapter(editor.editChapter(number, JSON.valueToTree(effects), adminId), editor.edited());
    }

    AdminChapter resetChapter(PrimalPrincipal principal, int number) {
        admins.require(principal);
        return chapter(editor.resetChapter(number), editor.edited());
    }

    /** {@code costs} — по уровню: материя → количество; в каталоге — материи по одной, как в forge.yaml. */
    AdminForgeItem editForge(PrimalPrincipal principal, String code, List<Map<String, Integer>> costs) {
        long adminId = admins.require(principal);
        List<List<String>> units = new ArrayList<>();
        for (Map<String, Integer> level : costs) {
            List<String> materials = new ArrayList<>();
            level.forEach((material, quantity) -> {
                if (quantity == null || quantity < 1 || quantity > UNIT_QUANTITY_MAX) {
                    throw invalid("costs", "Количество материи — от 1 до " + UNIT_QUANTITY_MAX + ".");
                }
                for (int i = 0; i < quantity; i++) {
                    materials.add(material);
                }
            });
            units.add(materials);
        }
        return forge(editor.editForge(code, JSON.valueToTree(units), adminId), editor.edited());
    }

    AdminForgeItem resetForge(PrimalPrincipal principal, String code) {
        admins.require(principal);
        return forge(editor.resetForge(code), editor.edited());
    }

    /** Растение цены: {@code any} — любое, иначе одно из {@code options}; в каталоге — как в lab.yaml. */
    AdminLabPotion editLab(PrimalPrincipal principal, String code, List<LabUnit> units) {
        long adminId = admins.require(principal);
        List<String> cost = new ArrayList<>();
        for (LabUnit unit : units) {
            if (unit.any()) {
                cost.add("ANY");
            } else if (unit.options() == null || unit.options().isEmpty()) {
                throw invalid("units", "Выберите растения или «любое растение».");
            } else {
                cost.add(unit.options().stream().distinct().map(Plant::name).collect(Collectors.joining("/")));
            }
        }
        return lab(editor.editLab(code, JSON.valueToTree(cost), adminId), editor.edited());
    }

    AdminLabPotion resetLab(PrimalPrincipal principal, String code) {
        admins.require(principal);
        return lab(editor.resetLab(code), editor.edited());
    }

    /** Стойки по уровням «0»…«3»; в каталоге — как в bosses.yaml: {@code {t, change}}. */
    AdminBoss editBoss(PrimalPrincipal principal, String code, Map<String, List<StanceInput>> difficulties) {
        long adminId = admins.require(principal);
        Map<String, List<Map<String, Object>>> yaml = new LinkedHashMap<>();
        difficulties.forEach((level, stances) -> yaml.put(level, stances.stream().map(stance -> {
            if (stance.mode() == null) {
                throw invalid("stances", "Укажите, когда меняется стойка.");
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("t", stance.toughnessPerHunter());
            if (stance.mode() == StanceChangeMode.HEALTH) {
                if (stance.atHealth() == null) {
                    throw invalid("stances", "Укажите здоровье, при котором меняется стойка.");
                }
                node.put("change", stance.atHealth());
            } else {
                node.put("change", stance.mode().name());
            }
            return node;
        }).toList()));
        return boss(editor.editBoss(code, JSON.valueToTree(yaml), adminId), editor.edited());
    }

    AdminBoss resetBoss(PrimalPrincipal principal, String code) {
        admins.require(principal);
        return boss(editor.resetBoss(code), editor.edited());
    }

    private static AdminBoss boss(BossDef def, Edited edited) {
        Boss view = Boss.of(def);
        return new AdminBoss(def.code(), def.name(), view.element(), view.expansion(), view.difficulties(),
                edited.bosses().contains(def.code()));
    }

    private AdminQuest quest(QuestDef def, Edited edited) {
        return new AdminQuest(def.number(), def.name(), def.bossCode(), catalog.bossName(def.bossCode()),
                def.expansion() == null ? null : def.expansion().name(),
                effects(def.victoryJson()), effects(def.expiredJson()),
                text(def.victory(), Context.QUEST), text(def.expired(), Context.QUEST),
                edited.quests().contains(def.number()));
    }

    private AdminChapter chapter(ChapterDef def, Edited edited) {
        return new AdminChapter(def.chapter(), effects(def.effectsJson()), text(def.effects(), Context.CHAPTER),
                edited.chapters().contains(def.chapter()));
    }

    private static AdminForgeItem forge(ForgeItemDef def, Edited edited) {
        ForgeItem view = ForgeItem.of(def);
        return new AdminForgeItem(def.code(), def.element(), def.name(), def.slot(), view.hunterClass(), view.costs(),
                edited.forge().contains(def.code()));
    }

    private static AdminLabPotion lab(LabPotionDef def, Edited edited) {
        return new AdminLabPotion(def.code(), def.name(), LabPotion.of(def).units(), edited.lab().contains(def.code()));
    }

    private static List<Map<String, Object>> effects(String json) {
        return JSON.readValue(json, new TypeReference<>() {
        });
    }

    /** Как это прочтут игроки: по строке на эффект верхнего уровня. */
    private List<String> text(List<Effect> effects, Context context) {
        EffectDescriber describer = catalog.describer();
        return effects.stream()
                .map(effect -> effect instanceof Effect.Conditional conditional
                        ? describer.describe(conditional, context)
                        : capitalize(describer.action(effect)))
                .toList();
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.")
                .with("errors", List.of(Map.of("field", field, "message", message)));
    }
}
