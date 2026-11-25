package br.com.validacarta.api;

import br.com.validacarta.pipeline.ArquivoInvalidoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Erros em formato ProblemDetail (RFC 9457): {"status":400,"detail":"..."} */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(ArquivoInvalidoException.class)
    public ProblemDetail arquivoInvalido(ArquivoInvalidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail arquivoGrande(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo maior que o limite de 10 MB");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail semArquivo(MissingServletRequestPartException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Envie o arquivo no campo 'arquivo'");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail requisicaoInvalida(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("Requisicao invalida");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
    }
}
