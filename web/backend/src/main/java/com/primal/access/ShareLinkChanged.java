package com.primal.access;

/** Ссылку кампании отозвали или перевыпустили: доступы по старой ссылке пропали — потоки событий проверяются. */
public record ShareLinkChanged(long campaignId) {
}
