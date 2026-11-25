package br.com.validacarta.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Verificacao a partir de um texto (pula OpenCV e Tesseract): bom para testar a IA e as regras sem foto. */
public record VerificacaoTextoRequest(
        @NotBlank(message = "Informe o texto da carta") @Size(max = 20000) String texto,
        @Size(max = 150) String nomeArquivo
) {
}
