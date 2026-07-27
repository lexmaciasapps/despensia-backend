package com.despensia.users.service;

import com.despensia.users.domain.User;
import com.despensia.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void testRegister_createsUserWithBcryptHash() {
        String email = "test@example.com";
        String rawPassword = "securePass123";
        String hashed = "$2a$10$hashedValue";

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(rawPassword)).thenReturn(hashed);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            u.setPasswordHash(hashed);
            return u;
        });

        User result = userService.register(email, rawPassword);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getRole()).isEqualTo(User.Role.USER);
        verify(passwordEncoder).encode(rawPassword);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testRegister_rejectsEmptyEmail() {
        assertThatThrownBy(() -> userService.register("", "password123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email is required");
    }

    @Test
    void testRegister_rejectsNullEmail() {
        assertThatThrownBy(() -> userService.register(null, "password123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email is required");
    }

    @Test
    void testRegister_rejectsInvalidEmailFormat() {
        assertThatThrownBy(() -> userService.register("notanemail", "password123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid email format");
    }

    @Test
    void testRegister_rejectsShortPassword() {
        assertThatThrownBy(() -> userService.register("test@example.com", "short"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRegister_rejectsBlankPassword() {
        assertThatThrownBy(() -> userService.register("test@example.com", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFindByEmail_returnsUserWhenFound() {
        String email = "user@example.com";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        User result = userService.findByEmail(email);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(email);
    }

    @Test
    void testFindByEmail_throwsWhenNotFound() {
        String email = "missing@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmail(email))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found");
    }

    @Test
    void testMatchesPassword_returnsTrueForCorrectPassword() {
        String email = "test@example.com";
        String rawPassword = "correctPass123";
        String hashed = "$2a$10$hashedValue";

        when(passwordEncoder.encode(rawPassword)).thenReturn(hashed);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq(rawPassword), eq(hashed))).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            u.setPasswordHash(hashed);
            return u;
        });

        User user = userService.register(email, rawPassword);

        assertThat(user.matchesPassword(rawPassword, passwordEncoder)).isTrue();
    }

    @Test
    void testMatchesPassword_returnsFalseForWrongPassword() {
        String email = "test@example.com";
        String correctPass = "correctPass123";
        String wrongPass = "wrongPass456";
        String hashed = "$2a$10$hashedValue";

        when(passwordEncoder.encode(correctPass)).thenReturn(hashed);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            u.setPasswordHash(hashed);
            return u;
        });

        User user = userService.register(email, correctPass);

        assertThat(user.matchesPassword(wrongPass, passwordEncoder)).isFalse();
    }

    @Test
    void testRegister_rejectsDuplicateEmail() {
        String email = "duplicate@example.com";
        String rawPassword = "securePass123";
        User existingUser = new User();
        existingUser.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> userService.register(email, rawPassword))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email already registered");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }
}
