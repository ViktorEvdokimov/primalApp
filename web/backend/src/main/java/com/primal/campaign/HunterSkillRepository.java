package com.primal.campaign;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface HunterSkillRepository extends JpaRepository<HunterSkill, HunterSkill.Key> {

    List<HunterSkill> findByKeyHunterIdIn(Collection<Long> hunterIds);
}
