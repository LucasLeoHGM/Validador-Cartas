"""
Etapa 2 do pipeline: pre-processamento da foto da carta com OpenCV.

Uso:  python3 scripts/preprocessar.py ARQUIVO_ENTRADA PREFIXO_SAIDA

Entrada: PNG, JPEG ou PDF (so a 1a pagina; precisa de pdf2image + poppler).
Saida:   4 PNG, um por parte da carta, cada uma tratada para o Tesseract:
           PREFIXO-cabecalho.png  nome da carta (faixa de cima)
           PREFIXO-carta.png      carta inteira
           PREFIXO-rodape.png     codigo do set, ATK/DEF e passcode (parte de baixo, ampliada)
           PREFIXO-binaria.png    carta inteira em preto e branco
Imprime no stdout um JSON com o resumo do que foi feito (mostrado na tela da aplicacao).

Passos:
  1. Procura na foto o contorno retangular da carta (bordas + contornos)
  2. Corrige a perspectiva: "desentorta" e recorta a carta, mesmo fotografada de lado ou inclinada
  3. Tons de cinza, remove ruido e ajusta contraste (CLAHE)
  4. Recorta cabecalho e rodape e amplia (o texto de baixo e minusculo)
  5. Se o fundo da faixa for escuro (texto claro), inverte para o Tesseract (que prefere texto escuro)

Limite: nao detecta carta de cabeca para baixo; envie a foto na posicao normal.
"""
import json
import sys

import cv2
import numpy as np

LARGURA_CARTA = 900        # a carta vira 900x1312 (proporcao 59:86 das cartas de Yu-Gi-Oh!)
ALTURA_CARTA = 1312
PROPORCAO = 59 / 86
AREA_MINIMA = 0.15         # a carta precisa ocupar pelo menos 15% da foto
AREA_JA_RECORTADA = 0.97   # acima disso a foto ja e a propria carta: nao ha o que recortar


def carregar(caminho):
    if caminho.lower().endswith(".pdf"):
        from pdf2image import convert_from_path  # import aqui: so e preciso para PDF
        paginas = convert_from_path(caminho, dpi=200, first_page=1, last_page=1)
        if not paginas:
            raise ValueError("PDF sem paginas")
        rgb = np.array(paginas[0].convert("RGB"))
        return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR), "pdf"
    # imdecode em vez de imread: funciona com acentos no caminho (Windows)
    dados = np.fromfile(caminho, dtype=np.uint8)
    img = cv2.imdecode(dados, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Arquivo nao e uma imagem valida")
    return img, "imagem"


def ordenar_pontos(pts):
    """Ordem: superior esquerdo, superior direito, inferior direito, inferior esquerdo."""
    pts = pts.reshape(4, 2).astype(np.float32)
    soma, dif = pts.sum(axis=1), np.diff(pts, axis=1).ravel()
    return np.array([pts[np.argmin(soma)], pts[np.argmin(dif)], pts[np.argmax(soma)], pts[np.argmax(dif)]], np.float32)


def achar_carta(img):
    """Devolve os 4 cantos da carta (na imagem original) ou None se nao achar um retangulo plausivel."""
    altura, largura = img.shape[:2]
    escala = 800 / max(altura, largura)
    pequena = cv2.resize(img, None, fx=escala, fy=escala, interpolation=cv2.INTER_AREA) if escala < 1 else img.copy()
    cinza = cv2.GaussianBlur(cv2.cvtColor(pequena, cv2.COLOR_BGR2GRAY), (5, 5), 0)
    bordas = cv2.Canny(cinza, 50, 150)
    bordas = cv2.dilate(bordas, np.ones((3, 3), np.uint8), iterations=2)
    contornos, _ = cv2.findContours(bordas, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    area_total = pequena.shape[0] * pequena.shape[1]

    for c in sorted(contornos, key=cv2.contourArea, reverse=True)[:8]:
        area = cv2.contourArea(c)
        if area < AREA_MINIMA * area_total:
            break
        if area > AREA_JA_RECORTADA * area_total:
            continue
        aprox = cv2.approxPolyDP(c, 0.02 * cv2.arcLength(c, True), True)
        if len(aprox) != 4 or not cv2.isContourConvex(aprox):
            continue
        cantos = ordenar_pontos(aprox)
        larg = (np.linalg.norm(cantos[1] - cantos[0]) + np.linalg.norm(cantos[2] - cantos[3])) / 2
        alt = (np.linalg.norm(cantos[3] - cantos[0]) + np.linalg.norm(cantos[2] - cantos[1])) / 2
        if min(larg, alt) == 0:
            continue
        proporcao = min(larg, alt) / max(larg, alt)
        if abs(proporcao - PROPORCAO) < 0.12:  # parece mesmo uma carta (59:86)
            return cantos / (escala if escala < 1 else 1.0), larg > alt
    return None


def endireitar(img, cantos, deitada):
    destino = np.array([[0, 0], [LARGURA_CARTA, 0], [LARGURA_CARTA, ALTURA_CARTA], [0, ALTURA_CARTA]], np.float32)
    if deitada:
        # a carta foi fotografada de lado: gira os cantos para ela ficar em pe
        cantos = np.roll(cantos, -1, axis=0)
    m = cv2.getPerspectiveTransform(cantos, destino)
    return cv2.warpPerspective(img, m, (LARGURA_CARTA, ALTURA_CARTA), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)


def realcar(cinza):
    """Contraste local (CLAHE) + um pouco de nitidez."""
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    c = clahe.apply(cinza)
    borrada = cv2.GaussianBlur(c, (0, 0), 2.0)
    return cv2.addWeighted(c, 1.6, borrada, -0.6, 0)


def texto_escuro(cinza):
    """O Tesseract le melhor texto escuro em fundo claro: se a faixa e escura, inverte."""
    return cv2.bitwise_not(cinza) if cinza.mean() < 110 else cinza


def processar(entrada, prefixo):
    img, origem = carregar(entrada)
    altura, largura = img.shape[:2]
    resumo = {"origem": origem, "tamanhoOriginal": f"{largura}x{altura}", "passos": []}

    achado = achar_carta(img)
    if achado is not None:
        cantos, deitada = achado
        carta = endireitar(img, cantos, deitada)
        resumo["passos"].append("carta encontrada e endireitada (correção de perspectiva)" + (", girada 90°" if deitada else ""))
        resumo["cartaEncontrada"] = True
    else:
        escala = ALTURA_CARTA / altura if altura >= largura else LARGURA_CARTA / largura
        carta = cv2.resize(img, None, fx=escala, fy=escala, interpolation=cv2.INTER_CUBIC)
        resumo["passos"].append("contorno da carta não encontrado: usando a foto inteira")
        resumo["cartaEncontrada"] = False

    cinza = cv2.cvtColor(carta, cv2.COLOR_BGR2GRAY)
    cinza = cv2.fastNlMeansDenoising(cinza, None, h=7, templateWindowSize=7, searchWindowSize=21)
    resumo["passos"].append("ruído removido")
    cinza = realcar(cinza)
    resumo["passos"].append("contraste ajustado")

    h, w = cinza.shape
    # faixas aproximadas de uma carta padrao: nome no topo, textos pequenos embaixo
    cabecalho = cinza[int(h * 0.025):int(h * 0.115), int(w * 0.04):int(w * 0.80)]
    rodape = cinza[int(h * 0.62):, :]
    cabecalho = cv2.resize(texto_escuro(cabecalho), None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC)
    rodape = cv2.resize(texto_escuro(rodape), None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC)
    resumo["passos"].append("nome e rodapé recortados e ampliados")

    binaria = cv2.adaptiveThreshold(cinza, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 31, 10)
    resumo["passos"].append("versão preto e branco")

    for nome, imagem in (("cabecalho", cabecalho), ("carta", cinza), ("rodape", rodape), ("binaria", binaria)):
        ok, png = cv2.imencode(".png", imagem)
        if not ok:
            raise ValueError("Falha ao gerar o PNG")
        png.tofile(f"{prefixo}-{nome}.png")
    resumo["tamanhoFinal"] = f"{w}x{h}"
    print(json.dumps(resumo, ensure_ascii=False))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("uso: preprocessar.py ENTRADA PREFIXO_SAIDA", file=sys.stderr)
        sys.exit(2)
    try:
        processar(sys.argv[1], sys.argv[2])
    except Exception as erro:  # o Java mostra a mensagem e segue com a imagem original
        print(f"erro no pre-processamento: {erro}", file=sys.stderr)
        sys.exit(1)
