package br.com.validacarta.pipeline;

public class ArquivoInvalidoException extends RuntimeException {

    public ArquivoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
