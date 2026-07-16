package com.rotacusto.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rotacusto.dto.request.AuthRequestDTO;
import com.rotacusto.dto.response.AuthResponseDTO;
import com.rotacusto.entity.User;
import com.rotacusto.repository.UserRepository;
import com.rotacusto.security.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponseDTO register(@Valid @RequestBody AuthRequestDTO request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException("Já existe uma conta com esse e-mail.");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setSenhaHash(passwordEncoder.encode(request.senha()));
        user.setCriadoEm(Instant.now());
        userRepository.save(user);
        return new AuthResponseDTO(jwtService.generateToken(user.getEmail()), user.getEmail());
    }

    @PostMapping("/login")
    public AuthResponseDTO login(@Valid @RequestBody AuthRequestDTO request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new IllegalArgumentException("E-mail ou senha inválidos."));
        if (!passwordEncoder.matches(request.senha(), user.getSenhaHash())) {
            throw new IllegalArgumentException("E-mail ou senha inválidos.");
        }
        return new AuthResponseDTO(jwtService.generateToken(user.getEmail()), user.getEmail());
    }
}
