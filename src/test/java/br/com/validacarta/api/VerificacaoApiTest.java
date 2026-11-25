package br.com.validacarta.api;

import br.com.validacarta.cartas.ConsultaFalsa;
import br.com.validacarta.cartas.ConsultaCartas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sobe a aplicacao com H2 em memoria e um banco oficial de mentira; a entrada em texto nao precisa de OpenCV nem Tesseract. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste;DB_CLOSE_DELAY=-1",
        "app.ia.provedor=regex"})
@AutoConfigureMockMvc
class VerificacaoApiTest {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ConsultaCartas consultaFalsa() {
            return new ConsultaFalsa();
        }
    }

    @Autowired
    MockMvc mvc;

    private static final String CARTA_VALIDA =
            "Dark Magician\\n[Spellcaster/Normal]\\nLOB-EN005\\nATK/2500 DEF/2100\\n46986414";

    @Test
    void verificaTextoSalvaEConsulta() throws Exception {
        String location = mvc.perform(post("/api/verificacoes/texto").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"" + CARTA_VALIDA + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.resultado.status").value("VERIFICADA"))
                .andExpect(jsonPath("$.cartaOficial.nome").value("Dark Magician"))
                .andExpect(jsonPath("$.etapas.length()").value(6))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartaOficial.id").value(46986414));
        mvc.perform(get("/api/verificacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").exists());
    }

    @Test
    void atkAlteradoEInconsistente() throws Exception {
        mvc.perform(post("/api/verificacoes/texto").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Dark Magician\\nLOB-EN005\\nATK/9999 DEF/2100\\n46986414\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado.status").value("INCONSISTENTE"));
    }

    @Test
    void textoVazioDa400() throws Exception {
        mvc.perform(post("/api/verificacoes/texto").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void arquivoBinarioDesconhecidoDa400() throws Exception {
        var arquivo = new MockMultipartFile("arquivo", "x.bin", "application/octet-stream", new byte[]{0, 1, 2, 3, 4, 5});
        mvc.perform(multipart("/api/verificacoes").file(arquivo)).andExpect(status().isBadRequest());
    }

    @Test
    void idInexistenteDa404() throws Exception {
        mvc.perform(get("/api/verificacoes/999999")).andExpect(status().isNotFound());
    }
}
