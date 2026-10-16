package fr.k0bus.cobblemon_jei.data

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Scanner universel pour découvrir et extraire les fichiers de données Cobblemon
 * (espèces, apparitions, datapacks) à travers tous les environnements (NeoForge, dev, production).
 */
object CobblemonDataScanner {

    private val logger = LoggerFactory.getLogger("CobblemonJEI/DataScanner")

    /**
     * Scanne toutes les sources disponibles pour trouver les fichiers JSON correspondant à [subPath]
     * (par exemple "species" ou "spawn_pool_world").
     * Appelle [consumer] pour chaque fichier unique trouvé : (cheminRelatif, fluxInputStream).
     */
    fun scanDataResources(
        subPath: String,
        consumer: (name: String, stream: InputStream) -> Unit
    ) {
        val seenSources = mutableSetOf<String>()

        // 1. Scan via l'API officielle NeoForge ModList (100% fiable en jeu et dev sur NeoForge)
        scanNeoForgeModList(subPath, seenSources, consumer)

        // 2. Scan via java.class.path (dev Gradle / IDE / launchers)
        scanJavaClassPath(subPath, seenSources, consumer)

        // 3. Scan via le ClassLoader (ressources marqueurs)
        scanClassLoaderResources(subPath, seenSources, consumer)

        // 4. Scan via CodeSource de classes de référence (PokemonSpecies, etc.)
        scanCodeSource(PokemonSpecies::class.java, subPath, seenSources, consumer)
        scanCodeSource(CobblemonDataScanner::class.java, subPath, seenSources, consumer)

        // 5. Scan du dossier mods/ de l'instance Minecraft
        scanModsDirectory(subPath, seenSources, consumer)

        // 6. Scan du ResourceManager du serveur local (si monde chargé)
        scanServerResourceManager(subPath, seenSources, consumer)

        // 7. Scan du ResourceManager client (si packs de ressources actifs)
        scanClientResourceManager(subPath, seenSources, consumer)

        logger.info("Cobblemon JEI: Scan pour '$subPath' terminé avec ${seenSources.size} fichiers traités.")
    }

    /**
     * Parcourt les conteneurs de mods NeoForge via [net.neoforged.fml.ModList].
     */
    private fun scanNeoForgeModList(
        subPath: String,
        seen: MutableSet<String>,
        consumer: (String, InputStream) -> Unit
    ) {
        try {
            val modListClass = Class.forName("net.neoforged.fml.ModList")
            val getMethod = modListClass.getMethod("get")
            val modList = getMethod.invoke(null) ?: return
            val getModFilesMethod = modListClass.getMethod("getModFiles")
            val modFiles = getModFilesMethod.invoke(modList) as? List<*> ?: return

            for (modFileInfo in modFiles) {
                if (modFileInfo == null) continue
                try {
                    val getFileMethod = modFileInfo.javaClass.getMethod("getFile")
                    val modFile = getFileMethod.invoke(modFileInfo) ?: continue
                    val findResourceMethod = modFile.javaClass.getMethod("findResource", Array<String>::class.java)

                    val dataPath = findResourceMethod.invoke(modFile, arrayOf("data")) as? Path
                    if (dataPath != null && Files.exists(dataPath)) {
                        Files.walk(dataPath).use { pathStream ->
                            pathStream.filter { p ->
                                val norm = p.toString().replace('\\', '/')
                                Files.isRegularFile(p) &&
                                        norm.contains("/$subPath/") &&
                                        norm.endsWith(".json")
                            }.forEach { p ->
                                val name = dataPath.relativize(p).toString().replace('\\', '/')
                                if (seen.add(name)) {
                                    try {
                                        Files.newInputStream(p).use { input ->
                                            consumer(name, input)
                                        }
                                    } catch (_: Throwable) {}
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logger.debug("Scan ModFile NeoForge exception: {}", e.message)
                }
            }
        } catch (e: Throwable) {
            logger.debug("NeoForge ModList non disponible ou non initialisé: {}", e.message)
        }
    }

    /**
     * Parcourt les entrées du classpath Java (notamment les JARs Cobblemon téléchargés par Gradle).
     */
    private fun scanJavaClassPath(
        subPath: String,
        seen: MutableSet<String>,
        consumer: (String, InputStream) -> Unit
    ) {
        try {
            val cp = System.getProperty("java.class.path") ?: return
            for (entry in cp.split(File.pathSeparator)) {
                if (entry.isBlank()) continue
                val file = File(entry)
                if (file.exists()) {
                    if (file.isFile && file.name.contains("cobblemon", ignoreCase = true) &&
                        (file.name.endsWith(".jar") || file.name.endsWith(".zip"))
                    ) {
                        scanZipFile(file, subPath, seen, consumer)
                    } else if (file.isDirectory && file.path.contains("cobblemon", ignoreCase = true)) {
                        scanDirectory(file, subPath, seen, consumer)
                    }
                }
            }
        } catch (e: Throwable) {
            logger.debug("Scan java.class.path exception: {}", e.message)
        }
    }

    /**
     * Utilise le ClassLoader pour localiser les fichiers JAR des mods contenant les ressources Cobblemon.
     */
    private fun scanClassLoaderResources(
        subPath: String,
        seen: MutableSet<String>,
        consumer: (String, InputStream) -> Unit
    ) {
        try {
            val cl = Thread.currentThread().contextClassLoader ?: PokemonSpecies::class.java.classLoader
            val marker = cl.getResource("assets/cobblemon/species.schema.json")
                ?: cl.getResource("data/cobblemon/global_species_features/blocks_traveled.json")
            if (marker != null) {
                val f = extractFileFromUrl(marker)
                if (f != null && f.exists()) {
                    if (f.isFile && (f.name.endsWith(".jar") || f.name.endsWith(".zip"))) {
                        scanZipFile(f, subPath, seen, consumer)
                    } else if (f.isDirectory) {
                        scanDirectory(f, subPath, seen, consumer)
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Inspecte le CodeSource de la classe pour extraire le fichier JAR ou dossier source.
     */
    private fun scanCodeSource(
        clazz: Class<*>,
        subPath: String,
        seen: MutableSet<String>,
        consumer: (String, InputStream) -> Unit
    ) {
        try {
            val loc = clazz.protectionDomain?.codeSource?.location ?: return
            val file = extractFileFromUrl(loc) ?: return
            if (file.exists()) {
                if (file.isFile && (file.name.endsWith(".jar") || file.name.endsWith(".zip"))) {
                    scanZipFile(file, subPath, seen, consumer)
                } else if (file.isDirectory) {
                    scanDirectory(file, subPath, seen, consumer)
                }
            }
        } catch (e: Throwable) {
            logger.debug("Scan CodeSource exception: {}", e.message)
        }
    }

    /**
     * Parcourt récursivement le dossier mods/ de l'instance Minecraft.
     */
    private fun scanModsDirectory(
        subPath: String,
        seen: MutableSet<String>,
        consumer: (String, InputStream) -> Unit
    ) {
        try {
            val gameDir = try { Minecraft.getInstance().gameDirectory } catch (_: Throwable) { File(".") }
            val modsDir = File(gameDir, "mods")
            if (modsDir.exists() && modsDir.isDirectory) {
                modsDir.walkTopDown().filter { it.isFile && it.name.endsWith(".jar") }.forEach { jar ->
                    scanZipFile(jar, subPath, seen, consumer)
                }
            }
        } catch (e: Throwable) {
            logger.debug("Scan modsDir exception: {}", e.message)
        }
    }

    /**
     * Parcourt les ressources du serveur solo s'il est actif.
     */
    private fun scanServerResourceManager(
        subPath: String,
        seen: MutableSet<String>,
        consumer: (String, InputStream) -> Unit
    ) {
        try {
            val server = Minecraft.getInstance().singleplayerServer ?: return
            val resManager = server.serverResources?.resourceManager ?: server.resourceManager
            val resources = resManager.listResources(subPath) { loc -> loc.path.endsWith(".json") }
            for ((loc, res) in resources) {
                val id = loc.toString()
                if (seen.add(id)) {
                    try {
                        res.open().use { stream ->
                            consumer(id, stream)
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Parcourt les ressources du client.
     */
    private fun scanClientResourceManager(
        subPath: String,
        seen: MutableSet<String>,
        consumer: (String, InputStream) -> Unit
    ) {
        try {
            val resManager = Minecraft.getInstance().resourceManager ?: return
            val resources = resManager.listResources(subPath) { loc -> loc.path.endsWith(".json") }
            for ((loc, res) in resources) {
                val id = loc.toString()
                if (seen.add(id)) {
                    try {
                        res.open().use { stream ->
                            consumer(id, stream)
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    private fun scanZipFile(
        jarFile: File,
        subPath: String,
        seen: MutableSet<String>,
        consumer: (String, InputStream) -> Unit
    ) {
        try {
            ZipFile(jarFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (!entry.isDirectory && name.contains("/$subPath/") && name.endsWith(".json")) {
                        if (seen.add(name)) {
                            try {
                                zip.getInputStream(entry).use { stream ->
                                    consumer(name, stream)
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger.debug("Scan Zip {} exception: {}", jarFile.name, e.message)
        }
    }

    private fun scanDirectory(
        dir: File,
        subPath: String,
        seen: MutableSet<String>,
        consumer: (String, InputStream) -> Unit
    ) {
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile && file.name.endsWith(".json")) {
                    val relPath = file.relativeTo(dir).path.replace('\\', '/')
                    if (relPath.contains("/$subPath/")) {
                        if (seen.add(relPath)) {
                            try {
                                file.inputStream().use { stream ->
                                    consumer(relPath, stream)
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger.debug("Scan dossier {} exception: {}", dir.name, e.message)
        }
    }

    /**
     * Extrait un [File] système valide depuis une [URL] (gère file:, jar:file:, union:).
     */
    private fun extractFileFromUrl(url: URL): File? {
        return try {
            val proto = url.protocol.lowercase()
            val path = url.path
            when {
                proto == "file" -> File(url.toURI())
                proto == "jar" -> {
                    val jarPath = path.substringBefore("!").removePrefix("file:")
                    File(URLDecoder.decode(jarPath, "UTF-8"))
                }
                proto == "union" -> {
                    val cleanPath = path.substringBefore("%23").substringBefore("!")
                    File(URLDecoder.decode(cleanPath, "UTF-8"))
                }
                else -> {
                    val sub = path.substringBefore("!").removePrefix("file:")
                    File(URLDecoder.decode(sub, "UTF-8"))
                }
            }
        } catch (_: Throwable) { null }
    }
}
