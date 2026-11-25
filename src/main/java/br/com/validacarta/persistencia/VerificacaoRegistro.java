package br.com.validacarta.persistencia;

import br.com.validacarta.dominio.StatusVerificacao;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Resultado salvo de uma verificacao. Nao guarda a foto nem o texto do OCR: so o que foi lido,
 * a carta oficial encontrada e o veredito.
 */
@Entity
@Table(name = "verificacao")
public class VerificacaoRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant criadoEm;

    @Column(length = 150)
    private String nomeArquivo;

    @Column(length = 20)
    private String tipoArquivo;

    @Column(length = 20)
    private String interpretador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVerificacao status;

    // o que foi lido na foto
    private String nomeLido;
    @Column(length = 30)
    private String codigoSetLido;
    @Column(length = 12)
    private String idCartaLido;
    @Column(length = 8)
    private String atkLido;
    @Column(length = 8)
    private String defLido;

    // a carta oficial encontrada no YGOPRODeck
    private Long cartaOficialId;
    private String cartaOficialNome;
    @Column(length = 100)
    private String cartaOficialTipo;
    @Column(length = 30)
    private String encontradaPor;
    @Column(length = 300)
    private String urlYgoprodeck;

    private long duracaoMs;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "verificacao_regra_atendida", joinColumns = @JoinColumn(name = "verificacao_id"))
    @Column(name = "regra", length = 300)
    private List<String> regrasAtendidas = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "verificacao_pendencia", joinColumns = @JoinColumn(name = "verificacao_id"))
    @Column(name = "pendencia", length = 400)
    private List<String> pendencias = new ArrayList<>();

    /** Etapas do pipeline em JSON (para mostrar o fluxo ao consultar uma verificacao antiga). */
    @Column(length = 10000)
    private String etapasJson;

    protected VerificacaoRegistro() {
        // exigido pelo JPA
    }

    public VerificacaoRegistro(Instant criadoEm, String nomeArquivo, String tipoArquivo, String interpretador,
                               StatusVerificacao status, String nomeLido, String codigoSetLido, String idCartaLido,
                               String atkLido, String defLido, Long cartaOficialId, String cartaOficialNome,
                               String cartaOficialTipo, String encontradaPor, String urlYgoprodeck, long duracaoMs,
                               List<String> regrasAtendidas, List<String> pendencias, String etapasJson) {
        this.criadoEm = criadoEm;
        this.nomeArquivo = nomeArquivo;
        this.tipoArquivo = tipoArquivo;
        this.interpretador = interpretador;
        this.status = status;
        this.nomeLido = nomeLido;
        this.codigoSetLido = codigoSetLido;
        this.idCartaLido = idCartaLido;
        this.atkLido = atkLido;
        this.defLido = defLido;
        this.cartaOficialId = cartaOficialId;
        this.cartaOficialNome = cartaOficialNome;
        this.cartaOficialTipo = cartaOficialTipo;
        this.encontradaPor = encontradaPor;
        this.urlYgoprodeck = urlYgoprodeck;
        this.duracaoMs = duracaoMs;
        this.regrasAtendidas = new ArrayList<>(regrasAtendidas);
        this.pendencias = new ArrayList<>(pendencias);
        this.etapasJson = etapasJson;
    }

    public Long getId() { return id; }
    public Instant getCriadoEm() { return criadoEm; }
    public String getNomeArquivo() { return nomeArquivo; }
    public String getTipoArquivo() { return tipoArquivo; }
    public String getInterpretador() { return interpretador; }
    public StatusVerificacao getStatus() { return status; }
    public String getNomeLido() { return nomeLido; }
    public String getCodigoSetLido() { return codigoSetLido; }
    public String getIdCartaLido() { return idCartaLido; }
    public String getAtkLido() { return atkLido; }
    public String getDefLido() { return defLido; }
    public Long getCartaOficialId() { return cartaOficialId; }
    public String getCartaOficialNome() { return cartaOficialNome; }
    public String getCartaOficialTipo() { return cartaOficialTipo; }
    public String getEncontradaPor() { return encontradaPor; }
    public String getUrlYgoprodeck() { return urlYgoprodeck; }
    public long getDuracaoMs() { return duracaoMs; }
    public List<String> getRegrasAtendidas() { return regrasAtendidas; }
    public List<String> getPendencias() { return pendencias; }
    public String getEtapasJson() { return etapasJson; }
}
