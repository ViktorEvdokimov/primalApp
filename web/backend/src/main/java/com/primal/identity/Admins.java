package com.primal.identity;

import com.primal.common.error.ApiException;
import com.primal.common.error.ErrorCode;
import com.primal.identity.PrimalPrincipal.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Администраторы ({@code app_admin}, qa № 137): правят награды заданий и глав. Назначаются только вручную скриптом
 * {@code deploy/grant-admin.sh}; на сайте роль не выдаётся и не снимается.
 */
@Service
public class Admins {

    private final JdbcTemplate jdbc;

    Admins(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isAdmin(long userId) {
        Integer count = jdbc.queryForObject("select count(*) from app_admin where user_id = ?", Integer.class, userId);
        return count != null && count > 0;
    }

    public boolean isAdmin(PrimalPrincipal principal) {
        return principal instanceof UserPrincipal user && isAdmin(user.userId());
    }

    /**
     * Только администратор; остальным — {@code 404}, как будто такого адреса нет: функция не должна быть видна
     * ни в каком виде. Возвращает id администратора — автора правки.
     */
    public long require(PrimalPrincipal principal) {
        if (principal instanceof UserPrincipal user && isAdmin(user.userId())) {
            return user.userId();
        }
        throw new ApiException(ErrorCode.NOT_FOUND, "Не найдено.");
    }
}
