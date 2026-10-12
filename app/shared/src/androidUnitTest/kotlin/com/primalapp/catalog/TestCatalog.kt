package com.primalapp.catalog

import java.io.File

/** Каталог по умолчанию из файла приложения (`androidApp/src/main/assets/catalog-bundle.json`). */
object TestCatalog {
    val catalog: Catalog by lazy {
        CatalogJson.parse(File("../androidApp/src/main/assets/catalog-bundle.json").readText())
    }
}
