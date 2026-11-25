"""
Gera fotos de teste SINTETICAS de carta em exemplos/ para a demonstracao.

Uso:  python3 scripts/gerar_exemplos.py

Cada imagem e uma carta generica (moldura simples, um quadro escrito "ARTE" e so texto) nas posicoes de uma
carta de verdade: nome em cima, codigo do set embaixo da arte, ATK/DEF e passcode no rodape. Depois a "foto"
e entortada em perspectiva, com sombra e ruido, para testar a correcao de perspectiva do OpenCV.
Nao ha arte nem moldura de carta real: para testar com cartas de verdade, tire fotos das suas.

Requer: pillow, numpy e opencv-python.
"""
import os

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

PASTA = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "exemplos")
FONTES = ["DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "arial.ttf",
          "C:/Windows/Fonts/arial.ttf", "/System/Library/Fonts/Supplemental/Arial.ttf"]
LARGURA, ALTURA = 590, 860  # proporcao 59:86


def fonte(tamanho, negrito=False):
    nomes = [f.replace("Sans.ttf", "Sans-Bold.ttf").replace("arial.ttf", "arialbd.ttf") for f in FONTES] if negrito else FONTES
    for nome in nomes:
        try:
            return ImageFont.truetype(nome, tamanho)
        except OSError:
            continue
    return ImageFont.load_default()


def desenhar_carta(nome, codigo_set, atk, defesa, passcode):
    img = Image.new("RGB", (LARGURA, ALTURA), (60, 50, 40))
    d = ImageDraw.Draw(img)
    d.rectangle([14, 14, LARGURA - 14, ALTURA - 14], fill=(205, 175, 110))            # moldura
    d.rectangle([34, 30, LARGURA - 34, 92], fill=(238, 228, 200), outline=(70, 55, 30), width=2)  # nome
    d.text((46, 40), nome, fill=(15, 15, 15), font=fonte(36, True))
    d.rectangle([62, 118, LARGURA - 62, 540], fill=(150, 150, 150), outline=(70, 55, 30), width=3)  # arte
    d.line([62, 118, LARGURA - 62, 540], fill=(120, 120, 120), width=2)
    d.line([62, 540, LARGURA - 62, 118], fill=(120, 120, 120), width=2)
    d.text((LARGURA // 2 - 40, 310), "ARTE", fill=(70, 70, 70), font=fonte(40, True))
    d.text((LARGURA - 200, 548), codigo_set, fill=(15, 15, 15), font=fonte(17, True))  # codigo do set
    d.rectangle([34, 580, LARGURA - 34, 790], fill=(238, 228, 200), outline=(70, 55, 30), width=2)
    d.text((46, 588), "[Spellcaster / Normal]", fill=(15, 15, 15), font=fonte(18, True))
    d.text((46, 622), "(descricao da carta)", fill=(60, 60, 60), font=fonte(18))
    d.text((LARGURA - 300, 756), f"ATK/{atk}  DEF/{defesa}", fill=(15, 15, 15), font=fonte(22, True))
    d.text((34, 812), passcode, fill=(15, 15, 15), font=fonte(15, True))                # passcode
    return np.array(img)


def fotografar(carta, inclinacao, semente):
    """Simula uma foto de celular: mesa escura, carta em perspectiva, sombra suave, ruido e desfoque leve."""
    rnd = np.random.default_rng(semente)
    h, w = 1500, 1200
    mesa = np.full((h, w, 3), (48, 62, 70), np.uint8)
    mesa = cv2.add(mesa, rnd.integers(0, 14, mesa.shape, dtype=np.uint8))
    origem = np.float32([[0, 0], [LARGURA, 0], [LARGURA, ALTURA], [0, ALTURA]])
    x0, y0 = 250, 230
    destino = np.float32([[x0, y0], [x0 + LARGURA, y0], [x0 + LARGURA, y0 + ALTURA], [x0, y0 + ALTURA]]) + np.float32(inclinacao)
    m = cv2.getPerspectiveTransform(origem, destino)
    foto = cv2.warpPerspective(carta, m, (w, h), dst=mesa, borderMode=cv2.BORDER_TRANSPARENT)
    sombra = np.tile(np.linspace(1.0, 0.78, w), (h, 1))[..., None]
    foto = (foto.astype(np.float32) * sombra + rnd.normal(0, 6, foto.shape)).clip(0, 255).astype(np.uint8)
    return cv2.GaussianBlur(foto, (3, 3), 0)


def salvar(nome_arquivo, carta, inclinacao, semente):
    caminho = os.path.join(PASTA, nome_arquivo)
    ok, png = cv2.imencode(".png", fotografar(cv2.cvtColor(carta, cv2.COLOR_RGB2BGR), inclinacao, semente))
    png.tofile(caminho)
    print("gerado", caminho)


def main():
    os.makedirs(PASTA, exist_ok=True)
    # (dx, dy) somados a cada canto: o sinal e o tamanho mudam o angulo e a perspectiva
    valida = desenhar_carta("Dark Magician", "LOB-EN005", "2500", "2100", "46986414")
    salvar("carta-valida.png", valida, [[18, 40], [-30, 12], [-10, -26], [30, -8]], 1)
    alterada = desenhar_carta("Dark Magician", "LOB-EN005", "9999", "2100", "46986414")
    salvar("carta-atk-alterado.png", alterada, [[-20, 10], [24, 36], [34, -14], [-18, -30]], 2)
    outro_set = desenhar_carta("Dark Magician", "LOB-EN001", "2500", "2100", "46986414")
    salvar("carta-set-de-outra-carta.png", outro_set, [[8, 22], [-14, -10], [26, -22], [-24, 18]], 3)


if __name__ == "__main__":
    main()
