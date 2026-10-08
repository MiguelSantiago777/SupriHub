package org.institutonata.suprihub.auth.api;

import java.time.Instant;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.auth.domain.AppUser;

public final class AuthDtos {

    static final String PASSWORD_REGEX = "^(?=.*[A-Za-zÀ-ÿ])(?=.*\\d).{8,72}$";
    static final String PASSWORD_MESSAGE = "A senha deve ter de 8 a 72 caracteres, com letras e números.";

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254)
            String email,
            @NotBlank(message = "Crie uma senha.") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password,
            @NotBlank(message = "Informe seu nome.") @Size(min = 3, max = 200, message = "Nome muito curto ou longo.")
            String fullName,
            String captchaToken) {

        @Override
        public String toString() {
            return "RegisterRequest[***]";
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.") String email,
            @NotBlank(message = "Informe sua senha.") String password,
            String captchaToken) {

        @Override
        public String toString() {
            return "LoginRequest[***]";
        }
    }

    public record EmailRequest(
            @NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.") String email,
            String captchaToken) {

        @Override
        public String toString() {
            return "EmailRequest[***]";
        }
    }

    public record TokenRequest(@NotBlank String token) {
        @Override
        public String toString() {
            return "TokenRequest[***]";
        }
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank(message = "Crie uma senha.") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String newPassword) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[***]";
        }
    }

    public record RenameRequest(
            @NotBlank(message = "Informe seu nome.") @Size(min = 3, max = 200, message = "Nome muito curto ou longo.")
            String fullName) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Informe sua senha atual.") String currentPassword,
            @NotBlank(message = "Crie uma senha.") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    public record ChangeEmailRequest(
            @NotBlank(message = "Informe o novo e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254)
            String newEmail,
            @NotBlank(message = "Informe sua senha atual.") String currentPassword) {

        @Override
        public String toString() {
            return "ChangeEmailRequest[***]";
        }
    }

    public record MeResponse(Long id, String fullName, String email, Set<AppRole> roles, boolean emailVerified) {
        public static MeResponse of(AppUser u) {
            return new MeResponse(u.getId(), u.getFullName(), u.getEmail(), u.roleCodes(), u.isEmailVerified());
        }
    }

    public record SessionResponse(String accessToken, Instant expiresAt, MeResponse user) {
    }

    public record MessageResponse(String message) {
    }

    public record UserDto(Long id, String fullName, String email, Set<AppRole> roles, boolean enabled,
            boolean emailVerified, Instant lastLoginAt, Instant createdAt, long version) {
        public static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.roleCodes(), u.isEnabled(),
                    u.isEmailVerified(), u.getLastLoginAt(), u.getCreatedAt(), u.getVersion());
        }
    }

    public record InviteUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 3, max = 200) String fullName,
            @NotEmpty(message = "Selecione ao menos um perfil.") Set<AppRole> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(min = 3, max = 200) String fullName,
            @NotEmpty(message = "Selecione ao menos um perfil.") Set<AppRole> roles,
            boolean enabled,
            long version) {
    }
}
