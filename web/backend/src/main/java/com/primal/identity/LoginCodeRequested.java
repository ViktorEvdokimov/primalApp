package com.primal.identity;

import java.time.Duration;

/** Код создан — письмо уходит после коммита транзакции ({@link LoginCodeMailSender}). */
record LoginCodeRequested(String email, String code, Duration validity) {

    /** Код не должен попасть в лог. */
    @Override
    public String toString() {
        return "LoginCodeRequested[email=" + email + "]";
    }
}
