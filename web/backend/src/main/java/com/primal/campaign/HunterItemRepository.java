package com.primal.campaign;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface HunterItemRepository extends JpaRepository<HunterItem, Long> {

    /** Инвентарь охотников в порядке получения. */
    List<HunterItem> findByHunterIdInOrderById(List<Long> hunterIds);
}
