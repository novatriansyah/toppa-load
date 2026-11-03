package com.toppaload.test;

import org.springframework.jdbc.core.simple.JdbcClient;

public final class TestDatabaseCleaner {

    private TestDatabaseCleaner() {
    }

    public static void cleanAll(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM audit_logs").update();
        jdbc.sql("DELETE FROM allocations").update();
        jdbc.sql("DELETE FROM leaves").update();
        jdbc.sql("DELETE FROM holidays").update();
        jdbc.sql("DELETE FROM tasks").update();
        jdbc.sql("DELETE FROM projects").update();
        jdbc.sql("DELETE FROM users WHERE email NOT IN ('admin@toppaload.local')").update();
    }
}

