package com.primal.catalog;

import com.primal.catalog.CatalogDtos.Achievement;
import com.primal.catalog.CatalogDtos.Boss;
import com.primal.catalog.CatalogDtos.DifficultyRange;
import com.primal.catalog.CatalogDtos.Dictionaries;
import com.primal.catalog.CatalogDtos.ForgeBoard;
import com.primal.catalog.CatalogDtos.ForgeItem;
import com.primal.catalog.CatalogDtos.LabPotion;
import com.primal.catalog.CatalogDtos.Named;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.rules.model.Element;
import com.primal.rules.model.HunterClass;
import com.primal.rules.model.Material;
import com.primal.rules.model.Plant;
import com.primal.rules.model.SkillBranch;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Справочники игры — без входа (их использует экспедиция). ETag — контрольная сумма каталога: повторный
 * запрос с {@code If-None-Match} получает 304 (проверяет Spring MVC по заголовку ETag ответа).
 */
@Tag(name = "catalog", description = "Справочники игры")
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private static final List<DifficultyRange> DIFFICULTY_BY_CHAPTER = List.of(
            new DifficultyRange(List.of(0), 0),
            new DifficultyRange(List.of(1, 2, 3), 1),
            new DifficultyRange(List.of(4, 5, 6, 7), 2),
            new DifficultyRange(List.of(8, 9, 10, 11), 3));

    private final CatalogService catalog;
    private final CatalogViews views;

    public CatalogController(CatalogService catalog, CatalogViews views) {
        this.catalog = catalog;
        this.views = views;
    }

    @GetMapping("/dictionaries")
    public ResponseEntity<Dictionaries> dictionaries() {
        Dictionaries dictionaries = new Dictionaries(
                Arrays.stream(Element.values())
                        .map(e -> new Named(e.name(), e.displayName(), e.expansion() == null ? null : e.expansion().name()))
                        .toList(),
                Arrays.stream(Material.values()).map(m -> new Named(m.name(), m.displayName(), null)).toList(),
                Arrays.stream(Plant.values()).map(p -> new Named(p.name(), p.displayName(), null)).toList(),
                Arrays.stream(HunterClass.values()).map(h -> new Named(h.name(), h.displayName(), null)).toList(),
                Arrays.stream(SkillBranch.values()).map(b -> new Named(b.name(), b.letter(), null)).toList(),
                DIFFICULTY_BY_CHAPTER);
        return cached(dictionaries);
    }

    @GetMapping("/bosses")
    public ResponseEntity<List<Boss>> bosses() {
        return cached(catalog.bosses().stream().map(Boss::of).toList());
    }

    @GetMapping("/achievements")
    public ResponseEntity<List<Achievement>> achievements() {
        return cached(catalog.achievements().stream().map(Achievement::of).toList());
    }

    @GetMapping("/quests")
    public ResponseEntity<List<CatalogViews.Quest>> quests() {
        return cached(catalog.quests().stream().map(views::quest).toList());
    }

    @GetMapping("/quests/{number}")
    public ResponseEntity<CatalogViews.Quest> quest(@PathVariable int number) {
        QuestDef quest = catalog.quest(number)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Задания " + number + " нет в каталоге."));
        return cached(views.quest(quest));
    }

    @GetMapping("/chapters")
    public ResponseEntity<List<CatalogViews.Chapter>> chapters() {
        return cached(catalog.chapters().stream().map(views::chapter).toList());
    }

    /** Планшеты кузни всех стихий в порядке справочника, со ценами на 3 уровня (doc/api.md §4). */
    @GetMapping("/forge")
    public ResponseEntity<List<ForgeBoard>> forge() {
        return cached(Arrays.stream(Element.values())
                .map(element -> new ForgeBoard(element, catalog.forge(element).stream().map(ForgeItem::of).toList()))
                .toList());
    }

    /** Планшет лаборатории: 6 зелий с растениями (doc/api.md §4). */
    @GetMapping("/lab")
    public ResponseEntity<List<LabPotion>> lab() {
        return cached(catalog.lab().stream().map(LabPotion::of).toList());
    }

    private <T> ResponseEntity<T> cached(T body) {
        return ResponseEntity.ok().cacheControl(CACHE).eTag(catalog.checksum()).body(body);
    }
}
