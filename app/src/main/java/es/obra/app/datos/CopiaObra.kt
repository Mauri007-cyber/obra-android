package es.obra.app.datos

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.File

/** Una copia portátil de una obra: datos y fotos, sin rutas del dispositivo original. */
object CopiaObra {
    private const val MAX_JSON = 4 * 1024 * 1024
    private const val MAX_FOTO = 20 * 1024 * 1024
    private const val MAX_TOTAL = 32 * 1024 * 1024
    private val json = Json { encodeDefaults = true }

    suspend fun exportar(contexto: Context, dao: ObraDao, obraId: Long, salida: OutputStream) = withContext(Dispatchers.IO) {
        val paquete = dao.instantaneaObra(obraId)
        validarRelaciones(paquete)
        val fotos = paquete.estancias.filter { it.foto.isNotBlank() }.associate { estancia ->
            val archivo = rutaFoto(contexto.filesDir, estancia.foto)
            require(archivo != null && archivo.isFile && archivo.length() in 1..MAX_FOTO.toLong()) { "Falta la foto de ${estancia.nombre}, o es demasiado grande" }
            estancia.id to archivo
        }
        val portable = paquete.copy(formato = 2, estancias = paquete.estancias.map {
            it.copy(foto = if (it.id in fotos) "fotos/${it.id}" else "")
        })
        val datos = json.encodeToString(portable).toByteArray(Charsets.UTF_8)
        require(datos.size <= MAX_JSON && fotos.values.sumOf { it.length() } + datos.size <= MAX_TOTAL) { "La obra es demasiado grande para esta copia" }
        ZipOutputStream(salida).use { zip ->
            zip.putNextEntry(ZipEntry("obra.json")); zip.write(datos); zip.closeEntry()
            fotos.forEach { (id, foto) ->
                zip.putNextEntry(ZipEntry("fotos/$id"))
                foto.inputStream().use { entrada -> copiarLimitado(entrada, zip, MAX_FOTO) }
                zip.closeEntry()
            }
        }
    }

    suspend fun importar(contexto: Context, dao: ObraDao, uri: Uri): Long = withContext(Dispatchers.IO) {
        val entradas = mutableMapOf<String, ByteArray>()
        var total = 0L
        val entrada = requireNotNull(contexto.contentResolver.openInputStream(uri)) { "No se puede abrir la copia" }
        ZipInputStream(entrada).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val nombre = entry.name
                require(!entry.isDirectory && (nombre == "obra.json" || nombre.matches(Regex("fotos/[1-9][0-9]*")))) { "Archivo inesperado en la copia" }
                require(nombre !in entradas && entradas.size < 1001) { "Archivo duplicado o demasiadas fotos" }
                val buffer = ByteArrayOutputStream()
                val restante = (MAX_TOTAL - total).toInt()
                copiarLimitado(zip, buffer, minOf(restante, if (nombre == "obra.json") MAX_JSON else MAX_FOTO))
                val bytes = buffer.toByteArray()
                total += bytes.size
                require(total <= MAX_TOTAL) { "La copia es demasiado grande" }
                entradas[nombre] = bytes
                zip.closeEntry()
            }
        }
        val datos = requireNotNull(entradas.remove("obra.json")) { "Falta obra.json" }
        val paquete = json.decodeFromString<ObraExport>(datos.toString(Charsets.UTF_8))
        require(paquete.formato == 2) { "Formato de copia no compatible" }
        validarRelaciones(paquete)
        val esperadas = paquete.estancias.mapNotNull { it.foto.takeIf(String::isNotBlank) }.toSet()
        require(esperadas == entradas.keys) { "Faltan fotos o hay archivos no referenciados" }
        val creadas = mutableListOf<File>()
        try {
            val carpeta = File(contexto.filesDir, "fotos").apply { check(isDirectory || mkdirs()) }
            val estancias = paquete.estancias.map { estancia ->
                if (estancia.foto.isBlank()) estancia else {
                    val bytes = entradas.getValue(estancia.foto)
                    require(bytes.isNotEmpty()) { "Foto vacía" }
                    val archivo = File(carpeta, "restaurada-${UUID.randomUUID()}.jpg")
                    creadas.add(archivo)
                    archivo.outputStream().use { it.write(bytes) }
                    estancia.copy(foto = "fotos/${archivo.name}")
                }
            }
            dao.importarObra(paquete.copy(estancias = estancias))
        } catch (error: Exception) {
            creadas.forEach { it.delete() }
            throw error
        }
    }
}

internal fun validarRelaciones(p: ObraExport) {
    require(p.obra.id > 0 && p.estancias.all { it.id > 0 && it.obraId == p.obra.id }) { "Estancias inválidas" }
    val estancias = p.estancias.map { it.id }.toSet()
    require(estancias.size == p.estancias.size) { "Estancias duplicadas" }
    require(p.muros.all { it.id > 0 && it.estanciaId in estancias }) { "Muros inválidos" }
    val muros = p.muros.map { it.id }.toSet()
    require(muros.size == p.muros.size) { "Muros duplicados" }
    require(p.huecos.all { it.id > 0 && it.muroId in muros } && p.huecos.map { it.id }.toSet().size == p.huecos.size) { "Huecos inválidos" }
    require(p.cambios.all { it.id > 0 && it.estanciaId in estancias } && p.cambios.map { it.id }.toSet().size == p.cambios.size) { "Cambios inválidos" }
    require(p.estancias.all { it.foto.isBlank() || (p.formato != 2 || it.foto == "fotos/${it.id}") }) { "Foto inválida" }
}

internal fun copiarLimitado(entrada: InputStream, salida: OutputStream, maximo: Int) {
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        val n = entrada.read(buffer)
        if (n < 0) return
        total += n
        require(total <= maximo) { "Archivo demasiado grande" }
        salida.write(buffer, 0, n)
    }
}
