// Tela de demonstracao: envia a foto da carta, mostra as 6 etapas do pipeline e o historico.
// Tudo que vem da API entra com textContent (nunca innerHTML), pois o texto do OCR pode conter qualquer coisa.

const ETAPAS = [
  { nome: "Carta", cor: "var(--azul)", papel: "Recebe a foto ou o PDF" },
  { nome: "OpenCV", cor: "var(--verde)", papel: "Acha a carta e endireita a foto" },
  { nome: "Tesseract", cor: "var(--roxo)", papel: "Lê nome, código, ATK/DEF e passcode" },
  { nome: "IA", cor: "var(--laranja)", papel: "Organiza o texto em campos (JSON)" },
  { nome: "YGOPRODeck", cor: "var(--teal)", papel: "Confere com o banco oficial" },
  { nome: "Resultado", cor: "var(--violeta)", papel: "Salva no banco e responde" },
];

const $ = (id) => document.getElementById(id);
let aba = "arquivo";
let arquivoEscolhido = null;
let exemplos = {};

function el(tag, props = {}, ...filhos) {
  const e = document.createElement(tag);
  Object.assign(e, props);
  filhos.forEach((f) => e.append(f));
  return e;
}

function desenharPipeline(etapas) {
  const area = $("pipeline");
  area.replaceChildren();
  ETAPAS.forEach((def, i) => {
    const info = etapas ? etapas.find((x) => x.ordem === i + 1) : null;
    const situacao = info ? info.situacao : "AGUARDANDO";
    const card = el("div", { className: "etapa" },
      el("h4", { textContent: `${i + 1}. ${def.nome}` }),
      el("div", { className: "papel", textContent: def.papel }),
      el("span", { className: `selo ${situacao}`, textContent: situacao }),
      el("div", { className: "detalhe", textContent: info ? resumirDetalhe(info) : "" }),
      el("div", { className: "tempo", textContent: info ? `${info.duracaoMs} ms` : "" }));
    card.style.setProperty("--cor", def.cor);
    area.append(card);
  });
}

// o OpenCV devolve um JSON com os passos; mostra so a lista
function resumirDetalhe(info) {
  if (info.ordem === 2 && info.detalhe && info.detalhe.startsWith("{")) {
    try { return JSON.parse(info.detalhe).passos.join("; "); } catch { /* mostra como veio */ }
  }
  return info.detalhe;
}

const NOMES_IMAGEM = { cabecalho: "nome", carta: "carta", rodape: "rodapé", binaria: "preto e branco", original: "original" };

function mostrarOficial(r) {
  const area = $("oficial");
  area.replaceChildren();
  area.className = "oficial";
  const c = r.cartaOficial;
  if (!c) {
    area.append(el("span", { className: "vazio", textContent: "Nenhuma carta oficial encontrada." }));
    return;
  }
  const lista = el("dl");
  const linha = (rotulo, valor) => { if (valor !== undefined && valor !== null && valor !== "") lista.append(el("dt", { textContent: rotulo }), el("dd", { textContent: String(valor) })); };
  linha("Nome", c.nome);
  linha("Tipo", c.tipo);
  linha("Passcode", c.id);
  linha("ATK / DEF", c.atk !== undefined || c.def !== undefined ? `${c.atk ?? "-"} / ${c.def ?? "-"}` : undefined);
  linha("Sets (exemplos)", c.codigosSet && c.codigosSet.length ? c.codigosSet.slice(0, 6).join(", ") : undefined);
  linha("Encontrada por", r.encontradaPor);
  area.append(lista);
  // so cria o link se for mesmo do YGOPRODeck (o texto vem de uma API externa)
  if (c.urlPagina && /^https:\/\/(db\.)?ygoprodeck\.com\//.test(c.urlPagina)) {
    area.append(el("p", {}, el("a", { href: c.urlPagina, target: "_blank", rel: "noopener noreferrer", textContent: "Ver no YGOPRODeck" })));
  }
}

function mostrarResultado(r) {
  $("detalhes").classList.remove("oculto");
  desenharPipeline(r.etapas);

  const img = $("imagem");
  img.replaceChildren();
  if (r.imagensTratadasBase64) {
    const grade = el("div", { className: "miniaturas" });
    Object.entries(r.imagensTratadasBase64).forEach(([nome, b64]) => grade.append(el("figure", {},
      el("img", { src: "data:image/png;base64," + b64, alt: "Imagem tratada: " + nome }),
      el("figcaption", { textContent: NOMES_IMAGEM[nome] || nome }))));
    img.append(grade);
  } else {
    img.append(el("span", { className: "vazio", textContent: r.textoExtraido === undefined
      ? "Imagens não são salvas (consulta do histórico)" : "Sem imagem (entrada em texto ou OpenCV não executado)" }));
  }
  $("textoExtraido").textContent = r.textoExtraido ?? "Texto não é salvo no banco.";
  $("interpretador").textContent = r.interpretador;
  $("json").textContent = JSON.stringify(r.dadosLidos, null, 2);
  mostrarOficial(r);

  const res = r.resultado;
  const icones = { VERIFICADA: "✓", INCONSISTENTE: "✗", REVISAO_MANUAL: "!" };
  const nomes = { VERIFICADA: "VERIFICADA", INCONSISTENTE: "INCONSISTENTE", REVISAO_MANUAL: "REVISÃO MANUAL" };
  const lista = el("ul", { className: "regras" });
  res.regrasAtendidas.forEach((t) => lista.append(el("li", { className: "sim", textContent: t })));
  res.pendencias.forEach((t) => lista.append(el("li", { className: t.startsWith("Aviso:") ? "aviso" : "nao", textContent: t })));
  $("resultado").replaceChildren(
    el("div", { className: `veredito ${res.status}` },
      el("span", { className: "icone", textContent: icones[res.status] }),
      el("span", { textContent: nomes[res.status] })),
    lista,
    el("p", { className: "vazio", textContent: `Verificação #${r.id} · ${r.duracaoMs} ms` }));
}

async function validar() {
  $("erro").textContent = "";
  const botao = $("validar");
  let resp;
  try {
    botao.disabled = true;
    botao.textContent = "Processando...";
    if (aba === "arquivo") {
      if (!arquivoEscolhido) throw new Error("Escolha um arquivo.");
      const form = new FormData();
      form.append("arquivo", arquivoEscolhido);
      resp = await fetch("/api/verificacoes", { method: "POST", body: form });
    } else {
      const texto = $("texto").value;
      if (!texto.trim()) throw new Error("Cole o texto da carta.");
      resp = await fetch("/api/verificacoes/texto", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ texto }),
      });
    }
    const corpo = await resp.json();
    if (!resp.ok) throw new Error(corpo.detail || `Erro ${resp.status}`);
    mostrarResultado(corpo);
    carregarHistorico();
  } catch (e) {
    $("erro").textContent = e.message;
  } finally {
    botao.disabled = false;
    botao.textContent = "Verificar carta";
  }
}

async function carregarHistorico() {
  const lista = await fetch("/api/verificacoes").then((r) => r.json());
  const corpo = $("historico");
  corpo.replaceChildren();
  lista.forEach((v) => {
    const tr = el("tr", {},
      el("td", { textContent: v.id }),
      el("td", { textContent: new Date(v.criadoEm).toLocaleString("pt-BR") }),
      el("td", { textContent: v.nomeArquivo }),
      el("td", { textContent: v.cartaOficialNome ?? "—" }),
      el("td", { textContent: v.interpretador }),
      el("td", {}, el("span", { className: `pilula ${v.status}`, textContent: v.status })));
    tr.onclick = async () => mostrarResultado(await fetch(`/api/verificacoes/${v.id}`).then((r) => r.json()));
    corpo.append(tr);
  });
}

async function carregarStatus() {
  const s = await fetch("/api/status").then((r) => r.json());
  const chip = (texto, ligado) => el("span", { className: ligado ? "" : "off", textContent: texto });
  $("status").replaceChildren(
    chip(`OpenCV: ${s.opencv ? "ligado" : "desligado"}`, s.opencv),
    chip(`Tesseract: ${s.tesseract ? "instalado (" + s.idiomaOcr + ")" : "não encontrado"}`, s.tesseract),
    chip(`Extração: ${s.provedorIaEmUso}${s.avisoIa ? " (" + s.avisoIa + ")" : ""}`, !s.avisoIa),
    chip("Banco oficial: YGOPRODeck", true));
}

function escolherArquivo(f) {
  arquivoEscolhido = f || null;
  $("nomeArquivo").textContent = f ? `${f.name} (${Math.ceil(f.size / 1024)} KB)` : "";
}

function iniciar() {
  desenharPipeline(null);
  document.querySelectorAll(".aba").forEach((b) => b.onclick = () => {
    aba = b.dataset.aba;
    document.querySelectorAll(".aba").forEach((x) => x.classList.toggle("ativa", x === b));
    $("formArquivo").classList.toggle("oculto", aba !== "arquivo");
    $("formTexto").classList.toggle("oculto", aba !== "texto");
  });
  $("arquivo").onchange = (e) => escolherArquivo(e.target.files[0]);
  const area = $("areaSoltar");
  area.ondragover = (e) => { e.preventDefault(); area.classList.add("arrastando"); };
  area.ondragleave = () => area.classList.remove("arrastando");
  area.ondrop = (e) => { e.preventDefault(); area.classList.remove("arrastando"); escolherArquivo(e.dataTransfer.files[0]); };
  document.querySelectorAll("[data-ex]").forEach((b) => b.onclick = () => { $("texto").value = exemplos[b.dataset.ex] || ""; });
  $("validar").onclick = validar;
  $("atualizar").onclick = carregarHistorico;
  document.querySelectorAll("form").forEach((f) => f.onsubmit = (e) => e.preventDefault());

  fetch("/api/exemplos").then((r) => r.json()).then((ex) => { exemplos = ex; });
  carregarStatus();
  carregarHistorico();
}

iniciar();
