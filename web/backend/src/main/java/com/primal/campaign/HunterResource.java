package com.primal.campaign;

import com.primal.rules.model.ResourceCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Количество ресурса у охотника. Изменяется атомарным upsert ({@link HunterResourceRepository#add}). */
@Entity
@Table(name = "hunter_resource")
public class HunterResource {

    @Embeddable
    public record Key(
            @Column(name = "hunter_id", nullable = false) long hunterId,
            @Column(nullable = false, length = 16) String resource) {
    }

    @EmbeddedId
    private Key key;

    @Column(nullable = false)
    private int quantity;

    protected HunterResource() {
    }

    public long getHunterId() {
        return key.hunterId();
    }

    public ResourceCode getResource() {
        return ResourceCode.valueOf(key.resource());
    }

    public int getQuantity() {
        return quantity;
    }
}
