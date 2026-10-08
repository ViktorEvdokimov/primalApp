package com.primal.admin;

import com.primal.common.api.ApiNullable;
import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.Admins;
import com.primal.identity.PrimalPrincipal;
import com.primal.info.InfoSection;
import com.primal.info.InfoService;
import com.primal.info.InfoService.ImportResult;
import com.primal.info.InfoService.InfoEntry;
import com.primal.info.InfoService.InfoSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Правка «Инфо» администратором (qa № 142): статьи всех разделов, картинки, импорт ключевых слов из файла правил.
 * Не администратору — 404, как и остальное администрирование.
 */
@Tag(name = "admin", description = "Администрирование: награды заданий и глав")
@RestController
@RequestMapping("/api/v1/admin/info")
class AdminInfoController {

    /**
     * {@code body} — абзацы через пустую строку; {@code **жирный**}; ссылки — «(См. также «Название».)».
     * {@code title} у символа реакции не нужен (не сохраняется), в остальных разделах обязателен.
     */
    record InfoEntryRequest(@NotNull InfoSection section, @ApiNullable String title, String body) {
    }

    private static final JsonMapper JSON = new JsonMapper();

    private final Admins admins;
    private final InfoService info;

    AdminInfoController(Admins admins, InfoService info) {
        this.admins = admins;
        this.info = info;
    }

    @Operation(operationId = "createInfoEntry")
    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    InfoEntry create(@AuthenticationPrincipal PrimalPrincipal principal, @Valid @RequestBody InfoEntryRequest body) {
        long adminId = admins.require(principal);
        return info.create(body.section(), body.title(), body.body(), adminId);
    }

    @Operation(operationId = "updateInfoEntry")
    @PutMapping("/entries/{id}")
    InfoEntry update(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long id,
                     @Valid @RequestBody InfoEntryRequest body) {
        long adminId = admins.require(principal);
        return info.update(id, body.section(), body.title(), body.body(), adminId);
    }

    @Operation(operationId = "deleteInfoEntry")
    @ApiResponse(responseCode = "204", description = "Статья удалена")
    @DeleteMapping("/entries/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long id) {
        admins.require(principal);
        info.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "uploadInfoImage")
    @PutMapping(path = "/entries/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    InfoEntry uploadImage(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long id,
                          @RequestPart("file") MultipartFile file) {
        long adminId = admins.require(principal);
        return info.setImage(id, bytes(file), adminId);
    }

    @Operation(operationId = "removeInfoImage")
    @DeleteMapping("/entries/{id}/image")
    InfoEntry removeImage(@AuthenticationPrincipal PrimalPrincipal principal, @PathVariable long id) {
        long adminId = admins.require(principal);
        return info.removeImage(id, adminId);
    }

    /** Выгрузка всего «Инфо» с картинками — файл по умолчанию для развёртывания и перенос на другой сайт. */
    @Operation(operationId = "exportInfo")
    @GetMapping("/export")
    ResponseEntity<InfoSnapshot> export(@AuthenticationPrincipal PrimalPrincipal principal) {
        admins.require(principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("default-info.json").build()
                        .toString())
                .body(info.export());
    }

    /** Всё «Инфо» — из выгрузки: текущие статьи и картинки заменяются. */
    @Operation(operationId = "restoreInfo")
    @PostMapping(path = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RestoreResult restore(@AuthenticationPrincipal PrimalPrincipal principal, @RequestPart("file") MultipartFile file) {
        long adminId = admins.require(principal);
        InfoSnapshot snapshot;
        try {
            snapshot = JSON.readValue(bytes(file), InfoSnapshot.class);
        } catch (JacksonException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.")
                    .with("errors", List.of(Map.of("field", "file", "message", "Это не выгрузка «Инфо».")));
        }
        return new RestoreResult(info.restore(snapshot, adminId));
    }

    /** Сколько статей теперь в «Инфо». */
    record RestoreResult(int restored) {
    }

    /** Markdown-файл правил: из раздела «Ключевые слова» добавляются статьи, которых ещё нет. */
    @Operation(operationId = "importInfoKeywords")
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportResult importKeywords(@AuthenticationPrincipal PrimalPrincipal principal,
                                @RequestPart("file") MultipartFile file) {
        long adminId = admins.require(principal);
        return info.importKeywords(bytes(file), adminId);
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
