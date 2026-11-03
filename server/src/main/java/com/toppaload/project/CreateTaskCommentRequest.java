package com.toppaload.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskCommentRequest(
        @NotBlank(message = "Komentar tidak boleh kosong")
        @Size(max = 2000, message = "Komentar maksimal 2000 karakter")
        String commentText
) {}

