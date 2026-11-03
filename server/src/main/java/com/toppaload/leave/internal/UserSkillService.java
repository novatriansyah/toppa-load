package com.toppaload.leave.internal;

import com.toppaload.leave.UpdateUserSkillsRequest;
import com.toppaload.leave.UserSkillDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
class UserSkillService {

    private final JdbcClient jdbc;

    UserSkillService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserSkillDto> getUserSkills(UUID userId) {
        return jdbc.sql("""
            SELECT id, user_id, skill_name, proficiency_level
            FROM user_skills
            WHERE user_id = :userId
            ORDER BY skill_name ASC
        """)
        .param("userId", userId)
        .query((rs, rowNum) -> new UserSkillDto(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                rs.getString("skill_name"),
                rs.getString("proficiency_level")
        ))
        .list();
    }

    @Transactional
    public List<UserSkillDto> updateUserSkills(UUID userId, UpdateUserSkillsRequest request) {
        jdbc.sql("DELETE FROM user_skills WHERE user_id = :userId")
                .param("userId", userId)
                .update();

        if (request.skills() != null) {
            for (var s : request.skills()) {
                if (s.skillName() != null && !s.skillName().isBlank()) {
                    jdbc.sql("""
                        INSERT INTO user_skills (id, user_id, skill_name, proficiency_level)
                        VALUES (gen_random_uuid(), :userId, :skillName, :proficiency)
                        ON CONFLICT (user_id, skill_name) DO UPDATE
                        SET proficiency_level = EXCLUDED.proficiency_level
                    """)
                    .param("userId", userId)
                    .param("skillName", s.skillName().trim())
                    .param("proficiency", s.proficiencyLevel() != null ? s.proficiencyLevel() : "INTERMEDIATE")
                    .update();
                }
            }
        }

        return getUserSkills(userId);
    }

    public List<UserSkillDto> getSkillsDirectory() {
        return jdbc.sql("""
            SELECT id, user_id, skill_name, proficiency_level
            FROM user_skills
            ORDER BY skill_name ASC, proficiency_level DESC
        """)
        .query((rs, rowNum) -> new UserSkillDto(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                rs.getString("skill_name"),
                rs.getString("proficiency_level")
        ))
        .list();
    }
}

