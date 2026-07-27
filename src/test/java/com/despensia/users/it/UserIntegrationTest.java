package com.despensia.users.it;

import com.despensia.product.service.ProductService;

import com.despensia.users.api.UsersController;
import com.despensia.users.domain.User;
import com.despensia.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for user registration and login endpoints.
 */
@AutoConfigureMockMvc
class UserIntegrationTest extends com.despensia.DespensiaBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    // ── Registration flow ────────────────────────────────

    @Test
    void register_validUser_returns201() throws Exception {
        String email = "integration@test.com";
        
        var response = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"securepass123"}
                    """.formatted(email)))
            .andExpect(status().isCreated())
            .andReturn();

        // Verify user was persisted in test DB
        User found = userService.findByEmail(email);
        assertThat(found).isNotNull();
        assertThat(found.getEmail()).isEqualTo(email);
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        String email = "dup@test.com";
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"securepass123"}
                    """.formatted(email)))
            .andExpect(status().isCreated());

        // Second registration with same email should fail
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"anotherpass456"}
                    """.formatted(email)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void register_weakPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"weak@test.com","password":"123"}
                    """))
            .andExpect(status().isBadRequest());
    }

    // ── Login flow ───────────────────────────────────────

    @Test
    void login_validCredentials_returns200() throws Exception {
        String email = "login@test.com";
        
        // Register first
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"securepass123"}
                    """.formatted(email)))
            .andExpect(status().isCreated());

        // Login with correct credentials
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"securepass123"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").exists());
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        String email = "wrong@test.com";
        
        // Register user with one password
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"correctpass"}
                    """.formatted(email)))
            .andExpect(status().isCreated());

        // Login with wrong password should fail
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"wrongpass"}
                    """.formatted(email)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void login_nonexistentUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"nonexist@test.com","password":"anypassword"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    // ── Password hashing verification ────────────────────

    @Test
    void password_isHashedNotPlaintext() {
        String email = "hashcheck@test.com";
        User user = userService.register(email, "mysecretpass");
        
        assertThat(user.getPasswordHash()).isNotNull();
        assertThat(user.getPasswordHash()).doesNotContain("mysecretpass"); // not plaintext
        
        // Verify matchesPassword works with BCrypt encoder
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        boolean matches = user.matchesPassword("mysecretpass", encoder);
        
        assertThat(matches).isTrue();
    }

    @Test
    void login_returnsUserIdAndName() throws Exception {
        String email = "namedata@test.com";
        
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"securepass123"}
                    """.formatted(email)))
            .andExpect(status().isCreated());

        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"securepass123"}
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andReturn();

        // Verify response contains userId and name (token is null until JWT phase)
        var mapper = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder()
                .build();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        
        assertThat(json.get("userId")).isNotNull();
        assertThat(json.get("name")).isEqualTo("integration integration");
    }

}
