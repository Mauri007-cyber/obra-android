# Obra completa: el replanteo

De "una foto con un prompt" a una obra entera modelada, evaluada y ensenada.

## Idea

Proyecto -> estancias -> cambios -> estado -> recorrido final.

1. La obra tiene las estancias que haga falta.
2. Cada estancia lleva cambios elegidos de un catalogo de mas de 45 partidas por oficio.
3. Cada cambio tiene cantidad, acabado y estado: aprobado, por decidir o descartado.
4. El cliente evalua, no adivina: aprueba trabajos concretos.
5. El recorrido final ensena solo lo aprobado, estancia por estancia.

## Decisiones

- La IA no esta en el camino critico: apagada por defecto y sin claves en el APK.
- El prompt se deduce de los cambios aprobados, en orden de trabajo y con una regla final que
  prohibe mover puertas y ventanas.
- Demoliciones y tabiques no se piden al modelo: van al plano y al presupuesto.
- El compositor por capas sobre la foto real es gratis y funciona sin conexion; la IA es extra.
- El modelo FLUX.2 klein solo admite fotos de entrada menores de 512x512: la app las reduce.

## Fases

F0 modelo, catalogo, aprobacion y recorrido: hecho.
F1 foto por estancia y compositor de capas: siguiente.
F2 luces, versiones A/B y lamina.
F3 puente con el presupuesto.
F4 IA por paquete de cambios y correccion por fallos.

El detalle completo vive en el ZIP del proyecto y en el chat que lo genero.
