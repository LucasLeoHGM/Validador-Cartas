package br.com.validacarta.api;

import br.com.validacarta.dominio.StatusVerificacao;
import br.com.validacarta.persistencia.VerificacaoRegistro;

import java.time.Instant;

/** Linha do historico. */
public record ResumoVerificacao(Long id, Instant criadoEm, String nomeArquivo, String cartaOficialNome,
                                String interpretador, StatusVerificacao status) {

    static ResumoVerificacao de(VerificacaoRegistro r) {
        return new ResumoVerificacao(r.getId(), r.getCriadoEm(), r.getNomeArquivo(), r.getCartaOficialNome(),
                r.getInterpretador(), r.getStatus());
    }
}
