package br.com.validacarta.dominio;

/**
 * Uma etapa do pipeline (Documento, OpenCV, Tesseract, IA, Regras, Resultado) e o que aconteceu nela.
 * situacao: OK, FALLBACK (usou um caminho alternativo), IGNORADA (nao se aplica) ou ERRO.
 */
public record EtapaPipeline(
        int ordem,
        String nome,
        String situacao,
        String detalhe,
        long duracaoMs
) {
    public static final String OK = "OK";
    public static final String FALLBACK = "FALLBACK";
    public static final String IGNORADA = "IGNORADA";
    public static final String ERRO = "ERRO";
}
