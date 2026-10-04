package com.primal.admin;

import com.primal.admin.AdminService.AdminCatalog;
import com.primal.admin.AdminService.AdminBoss;
import com.primal.admin.AdminService.AdminChapter;
import com.primal.admin.AdminService.AdminForgeItem;
import com.primal.admin.AdminService.AdminLabPotion;
import com.primal.admin.AdminService.AdminQuest;
import com.primal.admin.AdminService.StanceInput;
import com.primal.admin.AdminStatsService.AdminStats;
import com.primal.catalog.CatalogDtos.LabUnit;
import com.primal.identity.PrimalPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Правка каталога: награды заданий и глав (qa № 137), цены кузни и лаборатории (qa № 138; {@code doc/api.md} §9.4). Только администратору; остальным любой адрес
 * здесь отвечает {@code 404}, как несуществующий.
 */
@Tag(name = "admin", description = "Администрирование: награды заданий и глав")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    /** Списки эффектов на языке каталога ({@code doc/data-model.md} §4.3). */
    record QuestEffectsRequest(
            @NotNull @Schema(description = "Награды за победу") List<Map<String, Object>> victory,
            @NotNull @Schema(description = "Последствия невыполненного задания") List<Map<String, Object>> expired) {
    }

    record ChapterEffectsRequest(@NotNull @Schema(description = "Эффекты главы") List<Map<String, Object>> effects) {
    }

    /** Материи по уровням 1–3: материя → количество. */
    record ForgeCostRequest(@NotNull @Size(min = 3, max = 3) List<@NotNull Map<String, Integer>> costs) {
    }

    /** Стойки по уровням враждебности «0»…«3». */
    record BossStancesRequest(@NotNull Map<String, @NotNull List<@NotNull StanceInput>> difficulties) {
    }

    /** Растения цены зелья по одному. */
    record LabCostRequest(@NotNull @Size(min = 1, max = 4) List<@NotNull LabUnit> units) {
    }

    private final AdminService admin;
    private final AdminStatsService stats;

    AdminController(AdminService admin, AdminStatsService stats) {
        this.admin = admin;
        this.stats = stats;
    }

    @Operation(operationId = "getAdminStats")
    @GetMapping("/stats")
    AdminStats stats(@AuthenticationPrincipal PrimalPrincipal principal) {
        return stats.stats(principal);
    }

    @Operation(operationId = "getAdminCatalog")
    @GetMapping("/catalog")
    AdminCatalog catalog(@AuthenticationPrincipal PrimalPrincipal principal) {
        return admin.catalog(principal);
    }

    @Operation(operationId = "updateQuestRewards")
    @PutMapping("/quests/{number}")
    AdminQuest editQuest(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable int number,
                         @Valid @RequestBody QuestEffectsRequest body) {
        return admin.editQuest(principal, number, body.victory(), body.expired());
    }

    @Operation(operationId = "resetQuestRewards")
    @DeleteMapping("/quests/{number}")
    AdminQuest resetQuest(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable int number) {
        return admin.resetQuest(principal, number);
    }

    @Operation(operationId = "updateChapterRewards")
    @PutMapping("/chapters/{number}")
    AdminChapter editChapter(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable int number,
                             @Valid @RequestBody ChapterEffectsRequest body) {
        return admin.editChapter(principal, number, body.effects());
    }

    @Operation(operationId = "updateForgeCost")
    @PutMapping("/forge/{code}")
    AdminForgeItem editForge(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable String code,
                             @Valid @RequestBody ForgeCostRequest body) {
        return admin.editForge(principal, code, body.costs());
    }

    @Operation(operationId = "resetForgeCost")
    @DeleteMapping("/forge/{code}")
    AdminForgeItem resetForge(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable String code) {
        return admin.resetForge(principal, code);
    }

    @Operation(operationId = "updateLabCost")
    @PutMapping("/lab/{code}")
    AdminLabPotion editLab(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable String code,
                           @Valid @RequestBody LabCostRequest body) {
        return admin.editLab(principal, code, body.units());
    }

    @Operation(operationId = "resetLabCost")
    @DeleteMapping("/lab/{code}")
    AdminLabPotion resetLab(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable String code) {
        return admin.resetLab(principal, code);
    }

    @Operation(operationId = "updateBossStances")
    @PutMapping("/bosses/{code}")
    AdminBoss editBoss(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable String code,
                       @Valid @RequestBody BossStancesRequest body) {
        return admin.editBoss(principal, code, body.difficulties());
    }

    @Operation(operationId = "resetBossStances")
    @DeleteMapping("/bosses/{code}")
    AdminBoss resetBoss(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable String code) {
        return admin.resetBoss(principal, code);
    }

    @Operation(operationId = "resetChapterRewards")
    @DeleteMapping("/chapters/{number}")
    AdminChapter resetChapter(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable int number) {
        return admin.resetChapter(principal, number);
    }
}
