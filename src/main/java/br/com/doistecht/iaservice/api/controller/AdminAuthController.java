package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.AdminChangePasswordRequest;
import br.com.doistecht.iaservice.api.dto.AdminLoginRequest;
import br.com.doistecht.iaservice.api.dto.AdminSessionResponse;
import br.com.doistecht.iaservice.api.dto.AdminSessionResponse.AdminUserResponse;
import br.com.doistecht.iaservice.security.admin.AdminAccessService;
import br.com.doistecht.iaservice.security.admin.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Acesso", description = "Login e sessão do portal administrativo")
@RestController
@RequestMapping("/v1/admin/auth")
public class AdminAuthController {

	private final AdminAccessService accessService;

	public AdminAuthController(AdminAccessService accessService) {
		this.accessService = accessService;
	}

	@Operation(summary = "Autentica um administrador")
	@PostMapping("/login")
	public AdminSessionResponse login(@Valid @RequestBody AdminLoginRequest request) {
		return AdminSessionResponse.from(accessService.login(request.email(), request.password()));
	}

	@Operation(summary = "Informa o administrador autenticado")
	@GetMapping("/me")
	public AdminUserResponse me(@Parameter(hidden = true)
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal) {
		return AdminUserResponse.from(principal);
	}

	@Operation(summary = "Troca a senha e encerra as demais sessões")
	@PostMapping("/change-password")
	public AdminSessionResponse changePassword(@Valid @RequestBody AdminChangePasswordRequest request,
			@Parameter(hidden = true) @RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal) {
		return AdminSessionResponse.from(
				accessService.changePassword(principal, request.currentPassword(), request.newPassword()));
	}

	@Operation(summary = "Encerra a sessão atual")
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Parameter(hidden = true)
			@RequestAttribute(AdminPrincipal.REQUEST_ATTRIBUTE) AdminPrincipal principal) {
		accessService.logout(principal);
	}
}
