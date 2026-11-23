package es.obra.app.ia

import android.graphics.Bitmap

/**
 * Lo que sabe hacer un generador de imagen: recibir una foto y un texto, y devolver otra foto.
 *
 * La interfaz existe para que la pantalla no sepa si detrás hay un Worker propio, un servicio
 * futuro o una prueba. Si mañana cambia el proveedor, se cambia esta implementación y nada más.
 */
interface GeneradorImagen {

    /** Si falta la dirección del servicio, la app lo dice en vez de fallar al pulsar. */
    val configurado: Boolean

    suspend fun generar(foto: Bitmap, prompt: String, dispositivo: String): ResultadoImagen
}

sealed interface ResultadoImagen {

    /** La imagen ya decodificada a bytes (PNG o JPEG, lo que devuelva el servicio). */
    class Ok(val imagen: ByteArray, val restantes: Int?) : ResultadoImagen

    /** Se acabó la cuota gratuita del día. No es un error: es el límite conocido. */
    class SinCuota(val mensaje: String) : ResultadoImagen

    /** Cualquier otro fallo, ya traducido a una frase que se pueda enseñar. */
    class Fallo(val mensaje: String) : ResultadoImagen
}
