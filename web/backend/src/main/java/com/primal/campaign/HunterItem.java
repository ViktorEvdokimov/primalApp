package com.primal.campaign;

import com.primal.rules.model.Element;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Карта в инвентаре охотника: снаряжение (колода снаряжения), зелье (колода зелий) или карта награды.
 * {@code element} — стихия кузни, из которой снаряжение: при «продаже» карту можно сбросить вместо этой стихии.
 */
@Entity
@Table(name = "hunter_item")
public class HunterItem {

    public enum Kind { EQUIPMENT, POTION, REWARD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hunter_id", nullable = false)
    private long hunterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false, length = 100)
    private String name;

    private Short level;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Element element;

    @Column(length = 16)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HunterItem() {
    }

    HunterItem(long hunterId, Kind kind, String name, Integer level, Element element, String source, Instant now) {
        this.hunterId = hunterId;
        this.kind = kind;
        this.name = name;
        this.level = level == null ? null : level.shortValue();
        this.element = element;
        this.source = source;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getHunterId() {
        return hunterId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public Integer getLevel() {
        return level == null ? null : level.intValue();
    }

    public Element getElement() {
        return element;
    }

    public String getSource() {
        return source;
    }

    void edit(String name, Integer level) {
        this.name = name;
        this.level = level == null ? null : level.shortValue();
    }
}
