package com.primalapp.sync

import com.primalapp.catalog.Catalog
import com.primalapp.catalog.CatalogJson
import com.primalapp.database.PlatformContext
import com.primalapp.database.dao.CacheBlobDao
import com.primalapp.database.entity.CacheBlobEntity
import com.primalapp.database.readBundledText
import com.primalapp.info.InfoData
import com.primalapp.info.InfoJson
import com.primalapp.net.Fetched
import com.primalapp.net.PrimalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Откуда справочник: файл приложения (по умолчанию) или сервер. */
enum class ReferenceSource { DEFAULT, SERVER }

/**
 * Каталог игры: боссы, задания, главы, достижения, кузня, лаборатория. Без сети — последний полученный с сервера
 * (кэш в БД), а до первой синхронизации — файл приложения `catalog-bundle.json`. Онлайн — обновляется по ETag.
 */
class CatalogRepository(private val context: PlatformContext, private val cache: CacheBlobDao) {

    private val _catalog = MutableStateFlow<Catalog?>(null)
    val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()

    var source: ReferenceSource = ReferenceSource.DEFAULT
        private set

    val current: Catalog get() = _catalog.value ?: error("Каталог не загружен")

    suspend fun load() {
        val cached = cache.get(KEY)?.let { runCatching { CatalogJson.parse(it.data.decodeToString()) }.getOrNull() }
        if (cached != null) {
            source = ReferenceSource.SERVER
            _catalog.value = cached
            return
        }
        val bundled = readBundledText(context, BUNDLED) ?: error("Нет каталога по умолчанию $BUNDLED")
        source = ReferenceSource.DEFAULT
        _catalog.value = CatalogJson.parse(bundled)
    }

    /** Обновить с сервера; `true` — каталог изменился. Неразобранный ответ не заменяет действующий каталог. */
    suspend fun refresh(api: PrimalApi): Boolean {
        val etag = if (source == ReferenceSource.SERVER) cache.etag(KEY) else null
        return when (val fetched = api.catalogBundle(etag)) {
            Fetched.NotModified -> false
            is Fetched.Body -> {
                val parsed = CatalogJson.parse(fetched.value)
                cache.put(CacheBlobEntity(KEY, fetched.etag, fetched.value.encodeToByteArray()))
                val changed = _catalog.value?.version != parsed.version
                source = ReferenceSource.SERVER
                _catalog.value = parsed
                changed
            }
        }
    }

    private companion object {
        const val KEY = "catalog"
        const val BUNDLED = "catalog-bundle.json"
    }
}

/**
 * «Инфо»: ключевые слова, символы реакций, жетоны окружения. Без сети — последняя версия с сервера (статьи и
 * картинки в кэше), до первой синхронизации — файл приложения `default-info.json` с картинками внутри.
 */
class InfoRepository(private val context: PlatformContext, private val cache: CacheBlobDao) {

    private val _info = MutableStateFlow<InfoData?>(null)
    val info: StateFlow<InfoData?> = _info.asStateFlow()

    private var defaultImages: Map<String, ByteArray> = emptyMap()

    var source: ReferenceSource = ReferenceSource.DEFAULT
        private set

    suspend fun load() {
        val cached = cache.get(KEY)?.let { runCatching { InfoJson.parseServer(it.data.decodeToString()) }.getOrNull() }
        if (cached != null) {
            source = ReferenceSource.SERVER
            _info.value = cached
            return
        }
        loadDefault()
    }

    private fun loadDefault() {
        val text = readBundledText(context, BUNDLED) ?: return
        val parsed = runCatching { InfoJson.parseDefault(text) }.getOrNull() ?: return
        defaultImages = parsed.images
        source = ReferenceSource.DEFAULT
        _info.value = parsed.data
    }

    /** Обновить с сервера и докачать картинки — чтобы «Инфо» открывалось без сети. */
    suspend fun refresh(api: PrimalApi): Boolean {
        val etag = if (source == ReferenceSource.SERVER) cache.etag(KEY) else null
        val changed = when (val fetched = api.info(etag)) {
            Fetched.NotModified -> false
            is Fetched.Body -> {
                val parsed = InfoJson.parseServer(fetched.value)
                cache.put(CacheBlobEntity(KEY, fetched.etag, fetched.value.encodeToByteArray()))
                source = ReferenceSource.SERVER
                _info.value = parsed
                true
            }
        }
        _info.value?.entries?.mapNotNull { it.imageKey }?.filterNot { it.startsWith(InfoJson.DEFAULT_IMAGE_PREFIX) }?.forEach { path ->
            if (cache.etag(imageKey(path)) == null && cache.get(imageKey(path)) == null) {
                runCatching { api.infoImage(path) }.getOrNull()?.let { cache.put(CacheBlobEntity(imageKey(path), "", it)) }
            }
        }
        return changed
    }

    /** Картинка статьи: из файла по умолчанию, из кэша или (онлайн) с сервера. */
    suspend fun image(key: String, fetch: (suspend (String) -> ByteArray?)? = null): ByteArray? {
        if (key.startsWith(InfoJson.DEFAULT_IMAGE_PREFIX)) {
            if (defaultImages.isEmpty()) {
                readBundledText(context, BUNDLED)?.let { text -> runCatching { InfoJson.parseDefault(text) }.getOrNull() }
                    ?.let { defaultImages = it.images }
            }
            return defaultImages[key]
        }
        cache.get(imageKey(key))?.let { return it.data }
        val downloaded = fetch?.invoke(key) ?: return null
        cache.put(CacheBlobEntity(imageKey(key), "", downloaded))
        return downloaded
    }

    private fun imageKey(path: String) = "img:$path"

    private companion object {
        const val KEY = "info"
        const val BUNDLED = "default-info.json"
    }
}
