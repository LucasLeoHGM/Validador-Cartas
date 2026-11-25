package br.com.validacarta.cartas;

import br.com.validacarta.dominio.CartaOficial;

import java.io.IOException;
import java.util.Optional;

/**
 * Consulta ao banco oficial de cartas. Optional vazio = a carta nao existe;
 * IOException = o banco nao respondeu (fora do ar, timeout, limite de requisicoes).
 * Separar isso em interface permite testar sem internet.
 */
public interface ConsultaCartas {

    String nome();

    /** Pelo passcode de 8 digitos. idioma: pt, fr, de, it ou null (ingles). */
    Optional<CartaOficial> porId(String id, String idioma) throws IOException;

    /** Pelo codigo do set (ex.: LOB-EN005). */
    Optional<CartaOficial> porCodigoSet(String codigoSet, String idioma) throws IOException;

    /** Pelo nome: exato primeiro e, se nao achar, o mais parecido (busca aproximada). */
    Optional<CartaOficial> porNome(String nome, String idioma) throws IOException;
}
