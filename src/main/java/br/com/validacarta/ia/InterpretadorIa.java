package br.com.validacarta.ia;

import br.com.validacarta.dominio.DadosCarta;

import java.io.IOException;

/**
 * Etapa 4 do pipeline: transforma o texto do OCR em dados estruturados da carta.
 * Implementacoes: regex (sem IA), openai, gemini e claude. A escolha fica em app.ia.provedor.
 */
public interface InterpretadorIa {

    /** Nome usado em app.ia.provedor (regex, openai, gemini, claude). */
    String nome();

    /** true se a implementacao pode ser usada (ex.: tem chave de API). */
    boolean disponivel();

    DadosCarta interpretar(String texto) throws IOException;
}
