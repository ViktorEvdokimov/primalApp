package com.primal.catalog;

import com.primal.rules.model.StanceChangeMode;

/**
 * Стойка босса на одном уровне враждебности.
 *
 * @param stance             номер стойки, 1–9
 * @param toughnessPerHunter прочность за охотника; {@code null} — нет порога раны, урон только копится
 * @param changeMode         когда монстр переходит на следующую стойку
 * @param changeAtHealth     здоровье перехода для {@link StanceChangeMode#HEALTH}, иначе {@code null}
 */
public record StanceDef(int stance, Integer toughnessPerHunter, StanceChangeMode changeMode, Integer changeAtHealth) {
}
