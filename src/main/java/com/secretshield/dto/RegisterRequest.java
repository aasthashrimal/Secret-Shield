package com.secretshield.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.secretshield.validation.PasswordBytes;
import java.util.Locale;

public record RegisterRequest(

        @NotBlank(message = "Username is required")
        @Pattern(regexp = "[A-Za-z0-9_.-]{3,50}")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        @PasswordBytes
        String password
) {
    public RegisterRequest {
        username = username == null ? null : username.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
