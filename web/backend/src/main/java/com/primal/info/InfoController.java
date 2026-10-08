package com.primal.info;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.info.InfoService.Image;
import com.primal.info.InfoService.InfoView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * «Инфо» для всех, и без входа — его открывают из боя экспедиции (qa № 142). Статьи — с проверкой по ETag, картинки
 * не меняются (новая картинка — новый адрес) и кэшируются надолго.
 */
@Tag(name = "info", description = "Инфо: ключевые слова, символы реакций, жетоны окружения")
@RestController
@RequestMapping("/api/v1/info")
class InfoController {

    private final InfoService info;

    InfoController(InfoService info) {
        this.info = info;
    }

    @Operation(operationId = "getInfo")
    @GetMapping
    ResponseEntity<InfoView> info() {
        InfoView view = info.view();
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePublic()).eTag(view.version()).body(view);
    }

    @Operation(operationId = "getInfoImage")
    @GetMapping("/images/{id}")
    ResponseEntity<byte[]> image(@PathVariable UUID id) {
        Image image = info.image(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Картинки нет."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(image.data());
    }
}
