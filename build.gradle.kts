plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.148"
}

val modId = providers.gradleProperty("mod_id").get()
val neoVersion = providers.gradleProperty("neo_version").get()
val metadataProperties = listOf(
    "minecraft_version", "minecraft_version_range", "neo_version", "neo_version_range",
    "mod_id", "mod_name", "mod_license", "mod_version", "mod_authors", "mod_description"
).associateWith { providers.gradleProperty(it).get() }

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("mod_group_id").get()

base {
    archivesName = modId
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

// GameTests for the item handling (see src/gametest), run with runGameTestServer. Not part of the mod's jar.
val gametest: SourceSet = sourceSets.create("gametest") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

neoForge {
    enable {
        version = neoVersion
        // Run against the original compiled Minecraft rather than a recompiled one, so mixins that target
        // synthetic lambda names (e.g. Accessories, bundled by The Aether) find the same names as in production
        isDisableRecompilation = true
    }

    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")

    runs {
        register("client") {
            client()
            gameDirectory = layout.projectDirectory.dir("run")
            // -PquickPlay="World Name" joins that singleplayer world on launch
            providers.gradleProperty("quickPlay").orNull?.let { programArguments.addAll("--quickPlaySingleplayer", it) }
        }
        register("server") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-server")
            programArgument("--nogui")
        }
        // Runs the GameTests without any other mods, then stops
        register("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = layout.buildDirectory.dir("run-gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            sourceSet = gametest
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(gametest)
        }
    }
}

// The optional mods, from Modrinth's and CurseForge's Maven repositories, so the build works anywhere. Each is the
// exact file it was tested with (Modrinth by version id, CurseForge by file id).
repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository { maven("https://cursemaven.com") { name = "CurseMaven" } }
        filter { includeGroup("curse.maven") }
    }
}

// Run with -PwithoutBackpacked, -PwithoutQuark, -PwithoutCurios, -PwithoutAccessories, -PwithoutArsNouveau,
// -PwithoutSupplementaries, -PwithoutAether, -PwithoutJei and/or -PwithoutMouseTweaks to test without those optional
// dependencies, or -Pvanilla to run with no third-party mods at all (they are still compiled against)
val vanilla = providers.gradleProperty("vanilla").isPresent
fun without(flag: String) = vanilla || providers.gradleProperty(flag).isPresent

/** A mod (with the libraries it needs) for the -PwithoutX flag {@code flag}; {@code clientOnly} ones stay off servers. */
data class DevMod(val flag: String, val coordinates: List<String>, val clientOnly: Boolean = false)

val devMods = listOf(
    DevMod("withoutBackpacked", listOf("curse.maven:backpacked-352835:7866688", "curse.maven:framework-549225:7530361")), // 3.0.5, Framework 0.13.11
    DevMod("withoutQuark", listOf("maven.modrinth:quark:8uDGRJCr", "maven.modrinth:zeta:9GjNW2Gf")), // 4.1-485, Zeta 1.1-40
    DevMod("withoutCurios", listOf("maven.modrinth:curios:yohfFbgD")), // 9.5.1+1.21.1
    DevMod("withoutAccessories", listOf("maven.modrinth:accessories:Fb55Fgjz", "maven.modrinth:accessories-compat-layer:JMgVNypu",
        "maven.modrinth:owo-lib:NMCHU6DZ")), // 1.1.0-beta.53+1.21.1, compat layer 0.1.12, owo 0.12.15.5-beta.1+1.21
    DevMod("withoutArsNouveau", listOf("maven.modrinth:ars-nouveau:1VWyxABY", "maven.modrinth:geckolib:Grwn5rUB")), // 5.13.2, GeckoLib 4.9.3
    DevMod("withoutSupplementaries", listOf("maven.modrinth:supplementaries:WrZWfRjP", "maven.modrinth:moonlight:YfHkk7hg")), // 1.21.1-3.9.9, Moonlight 1.21.1-3.7.1
    DevMod("withoutAether", listOf("curse.maven:aether-255308:7043502")), // 1.21.1-1.5.10
    DevMod("withoutJei", listOf("maven.modrinth:jei:ufHUqt9b"), clientOnly = true), // 19.51.0.418
    DevMod("withoutMouseTweaks", listOf("maven.modrinth:mouse-tweaks:9I21YYxf"), clientOnly = true), // 2.26.1
)

/** Other mods to play-test alongside, from libs/ if they are there (by file name prefix), for the given flag. */
val extraDevMods = mapOf("FarmersDelight" to "", "mealmastery" to "", "suppsquared-" to "withoutSupplementaries")

val clientMods: Configuration = configurations.create("clientMods") { isTransitive = false }
val serverMods: Configuration = configurations.create("serverMods") { isTransitive = false }

// Third-party mods are compiled against, and loaded at runtime from run/mods (see syncClientMods), so that jar-in-jar
// dependencies (such as Quark's bundled Biolith) resolve exactly as they do in a real instance.
dependencies {
    // Ars Nouveau's GUI library, which isn't on either Maven yet (it is bundled inside Ars Nouveau at runtime)
    compileOnly(fileTree("libs") { include("nuggets-*.jar") })
    for(mod in devMods) {
        for(coordinates in mod.coordinates) {
            compileOnly(coordinates) { isTransitive = false }
            if(!without(mod.flag)) {
                clientMods(coordinates)
                if(!mod.clientOnly) {
                    serverMods(coordinates)
                }
            }
        }
    }
}

fun extraJars() = fileTree("libs").filter { jar ->
    !vanilla && extraDevMods.any { (prefix, flag) -> jar.name.startsWith(prefix) && (flag.isEmpty() || !without(flag)) }
}

// Resets a mods folder to the mods above. Not run automatically, so the run/mods and run-server/mods folders can be
// edited by hand between launches; run syncClientMods/syncServerMods (with any flags above) to reset them.
fun registerModSync(name: String, dir: String, mods: Configuration) = tasks.register(name) {
    group = "mod development"
    description = "Resets $dir to the optional mods to test with."
    doLast {
        // project.sync rather than a Sync task, which would skip (leaving the folder untouched) when -Pvanilla empties the list
        project.sync {
            from(mods)
            from(extraJars())
            into(layout.projectDirectory.dir(dir))
        }
    }
}
registerModSync("syncClientMods", "run/mods", clientMods)
registerModSync("syncServerMods", "run-server/mods", serverMods)
registerModSync("syncGameTestMods", "build/run-gametest/mods", serverMods)

/** Lists what syncClientMods and syncServerMods would put in the mods folders, without touching them. */
tasks.register("listDevMods") {
    group = "mod development"
    doLast {
        println("Client: " + (clientMods.files + extraJars().files).map { it.name }.sorted())
        println("Server: " + (serverMods.files + extraJars().files).map { it.name }.sorted())
    }
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    inputs.properties(metadataProperties)
    expand(metadataProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
