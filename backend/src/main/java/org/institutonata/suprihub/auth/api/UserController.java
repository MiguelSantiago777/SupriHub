package org.institutonata.suprihub.auth.api;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.institutonata.suprihub.auth.api.AuthDtos.InviteUserRequest;
import org.institutonata.suprihub.auth.api.AuthDtos.MeResponse;
import org.institutonata.suprihub.auth.api.AuthDtos.UpdateUserRequest;
import org.institutonata.suprihub.auth.api.AuthDtos.UserDto;
import org.institutonata.suprihub.auth.application.UserAdminService;
import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.shared.security.CurrentUser;
import org.institutonata.suprihub.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Usuários")
public class UserController {

    private final UserAdminService service;

    public UserController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping("/api/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Dados do usuário autenticado")
    public MeResponse me() {
        return MeResponse.of(service.get(CurrentUser.requireId()));
    }

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista/busca usuários")
    public PageResponse<UserDto> list(@RequestParam(required = false) AppRole role,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.search(role, q,
                PageRequest.of(page, Math.min(size, 100), Sort.by("fullName"))), UserDto::of);
    }

    @PostMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convida usuário interno (recebe link para definir a senha)")
    public UserDto invite(@Valid @RequestBody InviteUserRequest req) {
        return UserDto.of(service.invite(req.email(), req.fullName(), req.roles()));
    }

    @PutMapping("/api/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Atualiza nome, perfis e ativação")
    public UserDto update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest req) {
        return UserDto.of(service.update(id, req.fullName(), req.roles(), req.enabled(), req.version()));
    }
}
