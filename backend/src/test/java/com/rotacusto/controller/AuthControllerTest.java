package com.rotacusto.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.rotacusto.dto.request.AuthRequestDTO;
import com.rotacusto.entity.User;
import com.rotacusto.repository.UserRepository;
import com.rotacusto.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService("test-secret-key-only-for-automated-tests", 24);

    @Test
    void registerRejectsAWeakPassword() {
        AuthController controller = new AuthController(userRepository, passwordEncoder, jwtService);
        when(userRepository.existsByEmailIgnoreCase("nova@teste.com")).thenReturn(false);

        AuthRequestDTO request = new AuthRequestDTO("nova@teste.com", "abc123");

        assertThrows(IllegalArgumentException.class, () -> controller.register(request));
    }

    @Test
    void registerAcceptsAStrongPassword() {
        AuthController controller = new AuthController(userRepository, passwordEncoder, jwtService);
        when(userRepository.existsByEmailIgnoreCase("nova@teste.com")).thenReturn(false);

        AuthRequestDTO request = new AuthRequestDTO("nova@teste.com", "Abc12345");

        assertDoesNotThrow(() -> controller.register(request));
    }

    @Test
    void loginStillWorksForAnAccountCreatedBeforeThePasswordRuleExisted() {
        AuthController controller = new AuthController(userRepository, passwordEncoder, jwtService);

        // Conta antiga, cadastrada quando só a validação @Size(min = 6) existia.
        User contaAntiga = new User();
        contaAntiga.setEmail("antiga@teste.com");
        contaAntiga.setSenhaHash(passwordEncoder.encode("abc123"));
        contaAntiga.setCriadoEm(Instant.now());
        when(userRepository.findByEmailIgnoreCase("antiga@teste.com")).thenReturn(Optional.of(contaAntiga));

        AuthRequestDTO request = new AuthRequestDTO("antiga@teste.com", "abc123");

        assertDoesNotThrow(() -> controller.login(request));
    }
}
