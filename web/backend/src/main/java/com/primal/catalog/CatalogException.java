package com.primal.catalog;

/** Ошибка в файлах каталога: приложение не запускается, пока её не исправят. */
public class CatalogException extends RuntimeException {

    public CatalogException(String message) {
        super(message);
    }

    public CatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
