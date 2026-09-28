package com.primal.common.error;

import org.springframework.http.ProblemDetail;

/** Тело ошибки RFC 9457 с полем {@code code} ({@code doc/api.md} §1.1). */
public final class Problems {

    private Problems() {
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        return problem;
    }
}
