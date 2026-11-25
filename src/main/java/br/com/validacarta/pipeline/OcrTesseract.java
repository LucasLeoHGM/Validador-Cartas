package br.com.validacarta.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Etapa 3: extrai o texto da imagem com o Tesseract (executavel instalado na maquina).
 * O Tesseract so reconhece caracteres: ele nao entende o que e nome ou CPF - isso e a etapa 4.
 */
@Service
public class OcrTesseract {

    private final String comando;
    private final String idioma;
    private final String psm;
    private final String tessdata;
    private final long timeoutSegundos;
    private Boolean instalado;

    public OcrTesseract(@Value("${app.ocr.comando:tesseract}") String comando,
                        @Value("${app.ocr.idioma:eng}") String idioma,
                        @Value("${app.ocr.psm:11}") String psm,
                        @Value("${app.ocr.tessdata:}") String tessdata,
                        @Value("${app.ocr.timeout-segundos:60}") long timeoutSegundos) {
        this.comando = comando;
        this.idioma = idioma;
        this.psm = psm;
        this.tessdata = tessdata;
        this.timeoutSegundos = timeoutSegundos;
    }

    /** Verifica uma vez se o tesseract esta instalado. */
    public synchronized boolean instalado() {
        if (instalado == null) instalado = ComandoExterno.existe(comando);
        return instalado;
    }

    public String idioma() {
        return idioma;
    }

    /** psm padrao (app.ocr.psm). */
    public String extrairTexto(byte[] imagem) throws IOException {
        return extrairTexto(imagem, psm);
    }

    /**
     * Executa: tesseract IMAGEM stdout -l ... --psm N [--tessdata-dir DIR]
     * psm 7 = uma linha (nome da carta); psm 11 = texto espalhado, em qualquer posicao (carta inteira e rodape).
     */
    public String extrairTexto(byte[] imagem, String psm) throws IOException {
        Path arquivo = Files.createTempFile("validacarta-ocr-", ".png");
        try {
            Files.write(arquivo, imagem);
            List<String> cmd = new ArrayList<>(List.of(comando, arquivo.toString(), "stdout", "-l", idioma, "--psm", psm));
            if (tessdata != null && !tessdata.isBlank()) {
                cmd.add("--tessdata-dir");
                cmd.add(tessdata);
            }
            return ComandoExterno.executar(cmd, timeoutSegundos).strip();
        } finally {
            Files.deleteIfExists(arquivo);
        }
    }
}
