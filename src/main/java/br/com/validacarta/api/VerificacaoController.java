package br.com.validacarta.api;

import br.com.validacarta.cartas.YgoProDeckClient;
import br.com.validacarta.ia.SeletorInterpretador;
import br.com.validacarta.pipeline.ArquivoInvalidoException;
import br.com.validacarta.pipeline.OcrTesseract;
import br.com.validacarta.pipeline.PreprocessadorOpenCv;
import br.com.validacarta.pipeline.VerificacaoService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VerificacaoController {

    private final VerificacaoService service;
    private final PreprocessadorOpenCv openCv;
    private final OcrTesseract ocr;
    private final SeletorInterpretador interpretadores;
    private final YgoProDeckClient ygoprodeck;

    public VerificacaoController(VerificacaoService service, PreprocessadorOpenCv openCv, OcrTesseract ocr,
                                 SeletorInterpretador interpretadores, YgoProDeckClient ygoprodeck) {
        this.service = service;
        this.openCv = openCv;
        this.ocr = ocr;
        this.interpretadores = interpretadores;
        this.ygoprodeck = ygoprodeck;
    }

    /** Envia a foto da carta (PNG, JPG, PDF ou TXT) e roda o pipeline completo. */
    @PostMapping(value = "/verificacoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificacaoResposta> verificarArquivo(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        if (arquivo.isEmpty()) throw new ArquivoInvalidoException("Arquivo vazio");
        VerificacaoResposta r = service.verificarArquivo(nomeSeguro(arquivo.getOriginalFilename()), arquivo.getBytes());
        return ResponseEntity.created(URI.create("/api/verificacoes/" + r.id())).body(r);
    }

    /** Verifica a partir de um texto ja extraido (pula OpenCV e Tesseract). */
    @PostMapping(value = "/verificacoes/texto", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerificacaoResposta> verificarTexto(@Valid @RequestBody VerificacaoTextoRequest req) {
        VerificacaoResposta r = service.verificarTexto(nomeSeguro(req.nomeArquivo()), req.texto());
        return ResponseEntity.created(URI.create("/api/verificacoes/" + r.id())).body(r);
    }

    @GetMapping("/verificacoes")
    public List<ResumoVerificacao> historico() {
        return service.historico().stream().map(ResumoVerificacao::de).toList();
    }

    @GetMapping("/verificacoes/{id}")
    public ResponseEntity<VerificacaoResposta> buscar(@PathVariable Long id) {
        return ResponseEntity.of(service.buscar(id));
    }

    /** O que esta configurado em cada etapa (usado pela tela). Nao chama a API do YGOPRODeck. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("opencv", openCv.habilitado());
        s.put("tesseract", ocr.instalado());
        s.put("idiomaOcr", ocr.idioma());
        s.put("provedorIaConfigurado", interpretadores.provedorConfigurado());
        s.put("provedorIaEmUso", interpretadores.principal().nome());
        String motivo = interpretadores.motivoIndisponivel();
        if (motivo != null && !"regex".equals(interpretadores.provedorConfigurado())) s.put("avisoIa", motivo);
        s.put("ygoprodeck", ygoprodeck.urlBase());
        return s;
    }

    /**
     * Textos de exemplo, como se tivessem saido do OCR. Os dados das cartas (passcode, codigo do set, ATK/DEF)
     * sao os da primeira impressao de cada uma; o banco oficial e quem confirma.
     */
    @GetMapping("/exemplos")
    public Map<String, String> exemplos() {
        Map<String, String> ex = new LinkedHashMap<>();
        ex.put("valida", carta("Dark Magician", "LOB-EN005", "ATK/2500 DEF/2100", "46986414"));
        ex.put("atkAlterado", carta("Dark Magician", "LOB-EN005", "ATK/9999 DEF/2100", "46986414"));
        ex.put("setDeOutraCarta", carta("Dark Magician", "LOB-EN001", "ATK/2500 DEF/2100", "46986414"));
        ex.put("passcodeDeOutraCarta", carta("Dark Magician", "LOB-EN005", "ATK/2500 DEF/2100", "89631139"));
        ex.put("ocrRuim", carta("Dark Maglclan", "LOB-EN0O5", "ATK/2S00 DEF/21OO", "4698641A"));
        ex.put("incompleta", "Dark Magician\n[Spellcaster/Normal]");
        return ex;
    }

    private static String carta(String nome, String codigoSet, String pontos, String passcode) {
        return nome + "\n[Spellcaster/Normal]\n(descrição da carta)\n" + codigoSet + "\n" + pontos + "\n" + passcode;
    }

    /** Tira caracteres estranhos do nome do arquivo antes de salvar/mostrar. */
    static String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) return "carta";
        String n = nome.replaceAll("[\\\\/]", "_").replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return n.length() > 120 ? n.substring(0, 120) : n;
    }
}
