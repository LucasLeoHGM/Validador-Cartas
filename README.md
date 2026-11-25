# Validador de Cartas Yu-Gi-Oh!

Recebe a **foto de uma carta**, lê o que está impresso (nome, código do set, passcode, ATK/DEF) e **confere com a base oficial da API do YGOPRODeck**. O resultado é `VERIFICADA`, `INCONSISTENTE` ou `REVISAO_MANUAL`, sempre com os motivos.

> Não detecta falsificação física (papel, impressão, brilho). Só verifica se **o que está escrito na carta é coerente com uma carta que existe**.

## Pipeline

| # | Etapa | O que faz |
|---|-------|-----------|
| 1 | Carta | Recebe imagem (PNG/JPG/WebP) ou PDF |
| 2 | OpenCV | `scripts/preprocessar.py`: acha o contorno da carta, corrige a perspectiva (900x1312), reduz ruído, CLAHE e recorta cabeçalho, carta, rodapé e versão binária |
| 3 | Tesseract | OCR de cada recorte (cabeçalho em `psm 7`, resto em `psm 11`) |
| 4 | IA | Transforma o texto em JSON (`nome`, `codigoSet`, `idCarta`, `atk`, `def`). Provedor configurável: `regex` (padrão, offline), `openai`, `gemini`, `claude`. Falhou → cai para regex |
| 5 | YGOPRODeck | Busca a carta oficial por passcode → código do set → nome |
| 6 | Resultado | Aplica as regras e salva no banco |

Cada etapa informa `OK`, `FALLBACK`, `IGNORADA` ou `ERRO` na tela e na resposta da API.

## Regras

| Status | Quando |
|--------|--------|
| `VERIFICADA` | Pelo menos 2 campos lidos confirmados pela carta oficial e nenhuma contradição |
| `INCONSISTENTE` | Algum campo contradiz a base: código de set pertencente a outra carta, passcode de outra carta, ATK/DEF diferentes, nome que casa com outra carta |
| `REVISAO_MANUAL` | Poucos campos legíveis, carta não encontrada, ou API indisponível |

Detalhes: o idioma do código do set é ignorado (`LOB-EN005` = `LOB-PT005`); ATK/DEF `?` não são comparados; avisos não mudam o status.

## Como rodar

### Docker (tudo incluso)
```bash
docker compose up --build      # http://localhost:8080
```

### Local
Requisitos: Java 21, Python 3 com `pip install -r scripts/requirements.txt`, Tesseract com idioma `eng` (e `por` para cartas em português).

```bash
./mvnw test                    # Windows PowerShell: .\mvnw.cmd test
./mvnw spring-boot:run         # perfil dev: H2 em ./dados, console em /h2-console
```
No Windows, defina `OPENCV_COMANDO=python` e, se preciso, `TESSERACT_COMANDO` com o caminho do `tesseract.exe`.

### Variáveis
`IA_PROVEDOR` (regex/openai/gemini/claude), `OPENAI_API_KEY`, `GEMINI_API_KEY`, `ANTHROPIC_API_KEY`, `OCR_IDIOMA` (padrão `eng`; `eng+por`), `OPENCV_HABILITADO`, `YGOPRODECK_URL`, `DB_HOST/PORT/NAME/USER/PASSWORD` (perfil `prod`).

## API

| Método | Rota | |
|--------|------|-|
| POST | `/api/verificacoes` | multipart, campo `arquivo` |
| POST | `/api/verificacoes/texto` | `{"texto": "..."}` (pula OpenCV/OCR) |
| GET | `/api/verificacoes` · `/{id}` | histórico / detalhe |
| GET | `/api/status` | OpenCV, Tesseract e IA disponíveis |
| GET | `/api/exemplos` | textos de exemplo para demonstração |

## Demonstração rápida
Em `exemplos/` há 3 fotos sintéticas (geradas por `scripts/gerar_exemplos.py`): `carta-valida`, `carta-atk-alterado` (ATK diferente do oficial) e `carta-set-de-outra-carta`. Esperado: VERIFICADA, INCONSISTENTE, INCONSISTENTE.

## Decisões técnicas
- **Limite da API (20 req/s):** intervalo mínimo de 70 ms + cache em memória.
- **Imagens oficiais não são usadas** (a API pede para não fazer hotlink).
- **IA não decide autenticidade:** valores devolvidos só são aceitos se aparecem no texto do OCR (nome tolera erro de leitura).
- **Privacidade:** o banco guarda apenas o que foi lido, a carta oficial e o veredito; nem a imagem nem o texto do OCR.

## Limitações
- Não detecta cartas falsificadas fisicamente.
- Precisa de internet (YGOPRODeck).
- Foto de cabeça para baixo não é corrigida; brilho/holo e fontes especiais atrapalham o OCR.
- Um único dígito mal lido pode gerar `INCONSISTENTE` falso; por isso a mensagem pede conferência.
- O build Maven, os testes JUnit/Spring e chamadas reais à API não puderam ser executados no ambiente de desenvolvimento: rode `./mvnw test` primeiro.
