package br.com.validacarta.dominio;

public enum StatusVerificacao {
    /** Pelo menos 2 campos lidos conferem com o banco oficial e nenhum contradiz. */
    VERIFICADA,
    /** Algum campo lido contradiz o banco oficial (codigo do set de outra carta, ATK diferente...). */
    INCONSISTENTE,
    /** Leitura incerta: poucos campos, nome duvidoso ou API fora do ar. Nao quer dizer que a carta e falsa. */
    REVISAO_MANUAL
}
