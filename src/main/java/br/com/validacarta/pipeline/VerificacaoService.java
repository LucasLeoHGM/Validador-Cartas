package br.com.validacarta.pipeline;

import br.com.validacarta.api.VerificacaoResposta;
import br.com.validacarta.cartas.VerificadorCarta;
import br.com.validacarta.dominio.CartaOficial;
import br.com.validacarta.dominio.DadosCarta;
import br.com.validacarta.dominio.EtapaPipeline;
import br.com.validacarta.dominio.ResultadoVerificacao;
import br.com.validacarta.dominio.StatusVerificacao;
import br.com.validacarta.ia.ClienteLlm;
import br.com.validacarta.ia.InterpretadorIa;
import br.com.validacarta.ia.SeletorInterpretador;
import br.com.validacarta.persistencia.VerificacaoRegistro;
import br.com.validacarta.persistencia.VerificacaoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.validacarta.dominio.EtapaPipeline.ERRO;
import static br.com.validacarta.dominio.EtapaPipeline.FALLBACK;
import static br.com.validacarta.dominio.EtapaPipeline.IGNORADA;
import static br.com.validacarta.dominio.EtapaPipeline.OK;

/**
 * Orquestra o pipeline:  Carta -> OpenCV -> Tesseract -> IA -> YGOPRODeck (+ regras) -> Resultado (banco).
 * Uma falha numa etapa nao derruba a requisicao: a etapa usa um caminho alternativo e registra o que houve.
 */
@Service
public class VerificacaoService {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoService.class);

    /** Como cada parte da carta e lida: o nome e uma linha so (psm 7); o resto e texto espalhado (psm 11). */
    private static final Map<String, String> PSM_POR_VARIANTE = Map.of("cabecalho", "7");

    private final PreprocessadorOpenCv openCv;
    private final OcrTesseract ocr;
    private final SeletorInterpretador interpretadores;
    private final VerificadorCarta verificador;
    private final VerificacaoRepository repositorio;
    private final ObjectMapper json;
    private final Clock relogio;

    public VerificacaoService(PreprocessadorOpenCv openCv, OcrTesseract ocr, SeletorInterpretador interpretadores,
                              VerificadorCarta verificador, VerificacaoRepository repositorio, ObjectMapper json,
                              Optional<Clock> relogio) {
        this.openCv = openCv;
        this.ocr = ocr;
        this.interpretadores = interpretadores;
        this.verificador = verificador;
        this.repositorio = repositorio;
        this.json = json;
        this.relogio = relogio.orElse(Clock.systemDefaultZone());
    }

    public VerificacaoResposta verificarArquivo(String nomeArquivo, byte[] conteudo) {
        TipoArquivo tipo = TipoArquivo.detectar(conteudo);
        if (tipo == TipoArquivo.DESCONHECIDO) {
            throw new ArquivoInvalidoException("Formato não suportado. Envie PNG, JPG, PDF ou TXT.");
        }
        String texto = tipo == TipoArquivo.TEXTO ? new String(conteudo, StandardCharsets.UTF_8) : null;
        return executar(nomeArquivo, tipo, conteudo, texto);
    }

    public VerificacaoResposta verificarTexto(String nomeArquivo, String texto) {
        return executar(nomeArquivo == null || nomeArquivo.isBlank() ? "texto-informado.txt" : nomeArquivo,
                TipoArquivo.TEXTO, texto.getBytes(StandardCharsets.UTF_8), texto);
    }

    private VerificacaoResposta executar(String nomeArquivo, TipoArquivo tipo, byte[] conteudo, String textoInformado) {
        long inicio = System.currentTimeMillis();
        List<EtapaPipeline> etapas = new ArrayList<>();
        var cronometro = new Cronometro();

        // 1. Carta (arquivo recebido)
        etapas.add(new EtapaPipeline(1, "Carta", OK,
                nomeArquivo + " (" + tipo + ", " + tamanho(conteudo.length) + ")", cronometro.parcial()));

        // 2. OpenCV: acha a carta na foto, endireita e gera as imagens para o OCR
        Map<String, byte[]> imagensParaOcr = new LinkedHashMap<>();
        Map<String, String> imagensBase64 = new LinkedHashMap<>();
        if (tipo == TipoArquivo.TEXTO) {
            etapas.add(new EtapaPipeline(2, "OpenCV", IGNORADA, "Entrada em texto: não há imagem para tratar", cronometro.parcial()));
        } else if (!openCv.habilitado()) {
            usarOriginal(imagensParaOcr, conteudo, tipo);
            etapas.add(new EtapaPipeline(2, "OpenCV", tipo == TipoArquivo.PDF ? ERRO : FALLBACK,
                    tipo == TipoArquivo.PDF ? "OpenCV desabilitado: o PDF não pode ser convertido em imagem"
                            : "OpenCV desabilitado: usando a imagem original", cronometro.parcial()));
        } else {
            try {
                var tratada = openCv.processar(conteudo, tipo);
                imagensParaOcr.putAll(tratada.imagens());
                tratada.imagens().forEach((nome, png) -> imagensBase64.put(nome, Base64.getEncoder().encodeToString(png)));
                etapas.add(new EtapaPipeline(2, "OpenCV", OK, tratada.resumo(), cronometro.parcial()));
            } catch (IOException | RuntimeException e) {
                falha("OpenCV", e);
                usarOriginal(imagensParaOcr, conteudo, tipo);
                etapas.add(new EtapaPipeline(2, "OpenCV", tipo == TipoArquivo.PDF ? ERRO : FALLBACK,
                        "Falha no OpenCV (" + e.getMessage() + ")" + (tipo == TipoArquivo.PDF ? "" : ": usando a imagem original"),
                        cronometro.parcial()));
            }
        }

        // 3. Tesseract: le cada variante e junta os textos (o nome vem primeiro)
        String texto = textoInformado;
        if (tipo == TipoArquivo.TEXTO) {
            etapas.add(new EtapaPipeline(3, "Tesseract", IGNORADA,
                    "Texto informado diretamente (" + texto.length() + " caracteres)", cronometro.parcial()));
        } else if (imagensParaOcr.isEmpty()) {
            etapas.add(new EtapaPipeline(3, "Tesseract", ERRO, "Sem imagem para ler", cronometro.parcial()));
        } else if (!ocr.instalado()) {
            etapas.add(new EtapaPipeline(3, "Tesseract", ERRO, "Tesseract não instalado nesta máquina", cronometro.parcial()));
        } else {
            var partes = new StringBuilder();
            int lidas = 0;
            String ultimoErro = null;
            for (var variante : imagensParaOcr.entrySet()) {
                try {
                    String lidoNaImagem = ocr.extrairTexto(variante.getValue(), PSM_POR_VARIANTE.getOrDefault(variante.getKey(), "11"));
                    if (!lidoNaImagem.isBlank()) partes.append(lidoNaImagem).append('\n');
                    lidas++;
                } catch (IOException | RuntimeException e) {
                    falha("Tesseract", e);
                    ultimoErro = e.getMessage();
                }
            }
            texto = partes.toString().strip();
            etapas.add(new EtapaPipeline(3, "Tesseract", lidas > 0 ? OK : ERRO,
                    lidas > 0 ? texto.length() + " caracteres extraídos de " + lidas + " imagem(ns) (idioma " + ocr.idioma() + ")"
                            : "Falha no OCR: " + ultimoErro, cronometro.parcial()));
        }
        if (texto == null) texto = "";

        // 4. IA (ou regex)
        InterpretadorIa interpretador = interpretadores.principal();
        DadosCarta lido;
        if (texto.isBlank()) {
            lido = DadosCarta.vazio();
            etapas.add(new EtapaPipeline(4, "IA", IGNORADA, "Nenhum texto para interpretar", cronometro.parcial()));
        } else if (interpretador instanceof ClienteLlm llm) {
            try {
                lido = llm.interpretar(texto);
                etapas.add(new EtapaPipeline(4, "IA", OK,
                        "Interpretado por " + llm.nome() + " (" + llm.modelo() + ")", cronometro.parcial()));
            } catch (IOException | RuntimeException e) {
                falha("IA", e);
                var regex = interpretadores.fallback();
                interpretador = regex;
                lido = regex.interpretar(texto);
                etapas.add(new EtapaPipeline(4, "IA", FALLBACK,
                        "Falha na IA (" + e.getMessage() + "): extração por regex", cronometro.parcial()));
            }
        } else {
            lido = interpretadores.fallback().interpretar(texto);
            boolean regexEscolhido = "regex".equals(interpretadores.provedorConfigurado());
            etapas.add(new EtapaPipeline(4, "IA", regexEscolhido ? OK : FALLBACK,
                    regexEscolhido ? "Extração por regex (sem IA)"
                            : interpretadores.motivoIndisponivel() + ": extração por regex", cronometro.parcial()));
        }

        // 5. YGOPRODeck: acha a carta oficial e confere cada campo lido
        VerificadorCarta.Verificacao verificacao;
        try {
            verificacao = verificador.verificar(lido, texto);
            CartaOficial c = verificacao.carta();
            etapas.add(new EtapaPipeline(5, "YGOPRODeck", c != null ? OK : IGNORADA,
                    c != null ? c.nome() + " (passcode " + c.id() + "), encontrada por " + verificacao.encontradaPor()
                            + " - " + verificacao.resultado().regrasAtendidas().size() + " regra(s) atendida(s), "
                            + verificacao.resultado().pendencias().size() + " pendência(s)"
                            : "Carta não encontrada no banco oficial", cronometro.parcial()));
        } catch (IOException | RuntimeException e) {
            falha("YGOPRODeck", e);
            verificacao = new VerificadorCarta.Verificacao(null, null, new ResultadoVerificacao(
                    StatusVerificacao.REVISAO_MANUAL, List.of(),
                    List.of("Não foi possível consultar o YGOPRODeck (" + e.getMessage() + "). Tente de novo em instantes")));
            etapas.add(new EtapaPipeline(5, "YGOPRODeck", ERRO, "API indisponível: " + e.getMessage(), cronometro.parcial()));
        }
        ResultadoVerificacao resultado = verificacao.resultado();
        CartaOficial carta = verificacao.carta();

        // 6. Resultado: salva no banco
        etapas.add(new EtapaPipeline(6, "Resultado", OK, resultado.status() + " - salvo no banco", cronometro.parcial()));
        long duracao = System.currentTimeMillis() - inicio;
        VerificacaoRegistro registro = repositorio.save(new VerificacaoRegistro(
                Instant.now(relogio), nomeArquivo, tipo.name(), interpretador.nome(), resultado.status(),
                lido.nome(), lido.codigoSet(), lido.idCarta(), lido.atk(), lido.def(),
                carta == null ? null : carta.id(), carta == null ? null : carta.nome(),
                carta == null ? null : carta.tipo(), verificacao.encontradaPor(),
                carta == null ? null : carta.urlPagina(), duracao,
                resultado.regrasAtendidas(), resultado.pendencias(), paraJson(etapas)));

        return new VerificacaoResposta(registro.getId(), registro.getCriadoEm(), nomeArquivo, interpretador.nome(),
                duracao, etapas, texto, imagensBase64.isEmpty() ? null : imagensBase64, lido, carta,
                verificacao.encontradaPor(), resultado);
    }

    public Optional<VerificacaoResposta> buscar(Long id) {
        return repositorio.findById(id).map(this::deRegistro);
    }

    public List<VerificacaoRegistro> historico() {
        return repositorio.findTop50ByOrderByIdDesc();
    }

    /** Verificacao salva: sem texto do OCR e sem imagens. */
    private VerificacaoResposta deRegistro(VerificacaoRegistro r) {
        var lido = new DadosCarta(r.getNomeLido(), r.getCodigoSetLido(), r.getIdCartaLido(), r.getAtkLido(), r.getDefLido());
        CartaOficial carta = r.getCartaOficialId() == null ? null : new CartaOficial(r.getCartaOficialId(),
                r.getCartaOficialNome(), r.getCartaOficialTipo(), null, null, List.of(), List.of(), r.getUrlYgoprodeck());
        var resultado = new ResultadoVerificacao(r.getStatus(), List.copyOf(r.getRegrasAtendidas()), List.copyOf(r.getPendencias()));
        return new VerificacaoResposta(r.getId(), r.getCriadoEm(), r.getNomeArquivo(), r.getInterpretador(),
                r.getDuracaoMs(), deJson(r.getEtapasJson()), null, null, lido, carta, r.getEncontradaPor(), resultado);
    }

    /** Sem OpenCV: a imagem original vai direto para o OCR (PDF nao, porque o Tesseract nao le PDF). */
    private static void usarOriginal(Map<String, byte[]> destino, byte[] conteudo, TipoArquivo tipo) {
        if (tipo != TipoArquivo.PDF) destino.put("original", conteudo);
    }

    private String paraJson(List<EtapaPipeline> etapas) {
        try {
            return json.writeValueAsString(etapas);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<EtapaPipeline> deJson(String s) {
        if (s == null || s.isBlank()) return List.of();
        try {
            return json.readValue(s, new TypeReference<List<EtapaPipeline>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static String tamanho(int bytes) {
        return bytes < 1024 ? bytes + " B" : (bytes / 1024) + " KB";
    }

    private static void falha(String etapa, Exception e) {
        // so o tipo da excecao: a mensagem pode ter trechos do conteudo
        log.warn("Falha na etapa {} ({})", etapa, e.getClass().getSimpleName());
    }

    /** Tempo de cada etapa. */
    private static final class Cronometro {
        private long marca = System.currentTimeMillis();

        long parcial() {
            long agora = System.currentTimeMillis();
            long d = agora - marca;
            marca = agora;
            return d;
        }
    }
}
