package br.com.validacarta.dominio;

import java.util.List;

public record ResultadoVerificacao(
        StatusVerificacao status,
        List<String> regrasAtendidas,
        List<String> pendencias
) {
}
