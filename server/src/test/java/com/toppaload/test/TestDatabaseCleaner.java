package com.toppaload.test;

import org.springframework.jdbc.core.simple.JdbcClient;

public final class TestDatabaseCleaner {

    private TestDatabaseCleaner() {
    }

    public static void cleanAll(JdbcClient jdbc) {
        jdbc.sql("DELETE FROM scheduled_reports").update();
        jdbc.sql("DELETE FROM scenario_allocations").update();
        jdbc.sql("DELETE FROM scenarios").update();
        jdbc.sql("DELETE FROM calendar_feed_tokens").update();
        jdbc.sql("DELETE FROM audit_logs").update();
        jdbc.sql("DELETE FROM notifications").update();
        jdbc.sql("DELETE FROM saved_views").update();
        jdbc.sql("DELETE FROM task_comments").update();
        jdbc.sql("DELETE FROM user_skills").update();
        jdbc.sql("DELETE FROM leave_balances").update();
        jdbc.sql("DELETE FROM time_entries").update();
        jdbc.sql("DELETE FROM timesheet_submissions").update();
        jdbc.sql("DELETE FROM allocations").update();
        jdbc.sql("DELETE FROM leaves").update();
        jdbc.sql("DELETE FROM holidays").update();
        jdbc.sql("DELETE FROM tasks").update();
        jdbc.sql("DELETE FROM projects").update();
        jdbc.sql("UPDATE system_settings SET updated_by = NULL").update();
        jdbc.sql("DELETE FROM users WHERE email NOT IN ('admin@toppaload.local')").update();
    }
}
