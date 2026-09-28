package com.primal.campaign;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface HunterResourceRepository extends JpaRepository<HunterResource, HunterResource.Key> {

    /** Количество ресурса без загрузки сущностей: после нативных изменений в той же транзакции не устаревает. */
    interface Quantity {
        String getResource();

        int getQuantity();
    }

    List<HunterResource> findByKeyHunterIdIn(Collection<Long> hunterIds);

    @Query("select r.key.resource as resource, r.quantity as quantity from HunterResource r where r.key.hunterId = :hunterId")
    List<Quantity> quantities(long hunterId);

    /** Начисление: атомарный upsert. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into hunter_resource (hunter_id, resource, quantity) values (:hunterId, :resource, :amount)
            on conflict (hunter_id, resource) do update set quantity = hunter_resource.quantity + excluded.quantity""")
    void add(long hunterId, String resource, int amount);

    /**
     * Списание. Не upsert: PostgreSQL проверяет CHECK у вставляемой строки ещё до {@code on conflict}, и
     * отрицательное количество отклонялось бы даже при достаточном запасе. Уход ниже нуля отклоняет CHECK;
     * 0 изменённых строк — ресурса нет вовсе.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            update hunter_resource set quantity = quantity - :amount where hunter_id = :hunterId and resource = :resource""")
    int subtract(long hunterId, String resource, int amount);
}
