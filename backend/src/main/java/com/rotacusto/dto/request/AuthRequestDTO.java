package com.rotacusto.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Usado tanto pro registro quanto pro login — mesmo shape, sem motivo pra duas classes. */
public record AuthRequestDTO(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, message = "A senha precisa ter pelo menos 6 caracteres.") String senha) {
}
