package br.com.doistecht.iaservice.security.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.doistecht.iaservice.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AdminAccessIT extends AbstractIntegrationTest {

	@Autowired
	private AdminUserRepository users;

	@Autowired
	private PasswordHasher passwordHasher;

	@Test
	void shouldRequirePasswordChangeAndRevokeOldSessions() throws Exception {
		String email = "admin-" + UUID.randomUUID() + "@doistechti.test";
		String currentPassword = "senha-antiga-123";
		String newPassword = "senha-nova-4567";
		users.save(new AdminUser(email, passwordHasher.hash(currentPassword), true));

		String firstLogin = login(email, currentPassword)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.mustChangePassword").value(true))
				.andReturn().getResponse().getContentAsString();
		String oldToken = JsonPath.read(firstLogin, "$.token");

		mockMvc.perform(get("/v1/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer(oldToken)))
				.andExpect(status().isForbidden());

		String changed = mockMvc.perform(post("/v1/admin/auth/change-password")
					.header(HttpHeaders.AUTHORIZATION, bearer(oldToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"currentPassword":"%s","newPassword":"%s"}
							""".formatted(currentPassword, newPassword)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.mustChangePassword").value(false))
				.andReturn().getResponse().getContentAsString();
		String newToken = JsonPath.read(changed, "$.token");

		mockMvc.perform(get("/v1/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer(oldToken)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/v1/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer(newToken)))
				.andExpect(status().isOk());

		login(email, currentPassword).andExpect(status().isUnauthorized());
		login(email, newPassword).andExpect(status().isOk())
				.andExpect(jsonPath("$.user.mustChangePassword").value(false));
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/v1/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)));
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}
}
