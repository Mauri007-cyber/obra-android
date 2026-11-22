#!/usr/bin/env python3
"""Pinta a PNG un volcado de caras proyectadas (JSON) para revisar el motor 3D."""
import json
import sys

from PIL import Image, ImageDraw

ANCHO, ALTO = 1200, 900
ESCALA = 2  # supermuestreo para suavizar bordes


def argb(valor):
    alfa = (valor >> 24) & 0xFF
    rojo = (valor >> 16) & 0xFF
    verde = (valor >> 8) & 0xFF
    azul = valor & 0xFF
    return (rojo, verde, azul, alfa)


def pintar(origen, destino):
    caras = json.load(open(origen))

    imagen = Image.new("RGB", (ANCHO * ESCALA, ALTO * ESCALA), (247, 245, 240))
    lienzo = ImageDraw.Draw(imagen, "RGBA")

    for cara in caras:
        puntos = cara["pts"]
        pares = [
            (puntos[i] * ESCALA, puntos[i + 1] * ESCALA)
            for i in range(0, len(puntos), 2)
        ]
        if len(pares) < 3:
            continue
        relleno = argb(cara["color"])
        borde = argb(cara["contorno"])
        lienzo.polygon(pares, fill=relleno, outline=borde)

    imagen = imagen.resize((ANCHO, ALTO), Image.LANCZOS)
    imagen.save(destino)
    print(f"{destino}: {len(caras)} caras")


if __name__ == "__main__":
    pintar(sys.argv[1], sys.argv[2])
