package com.despensia.users.api;

import com.despensia.users.domain.User;
import com.despensia.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User authentication and registration operations")
public class UsersController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/auth/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account with email, name derived from email, and BCrypt-hashed password.")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.register(request.email(), request.password());
            return ResponseEntity.status(201).body(new RegisterResponse(user.getId(), user.getEmail(), user.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Authenticate a user", description = "Validates credentials and returns user info. JWT token generation deferred.")
    @ApiResponse(responseCode = "200", description = "Authentication successful")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            User user = userService.findByEmail(request.email());
            if (!user.matchesPassword(request.password(), passwordEncoder)) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
            }
            // TODO: generate JWT token once auth infrastructure is in place
            return ResponseEntity.ok(new AuthResponse(user.getId(), user.getName(), null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    public record RegisterRequest(String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record AuthResponse(java.util.UUID userId, String name, String token) {}
    public record RegisterResponse(java.util.UUID id, String email, String name) {}
}
