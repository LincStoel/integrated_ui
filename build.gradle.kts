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

neoForge {
    version = neoVersion

    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")

    runs {
        register("client") {
            client()
            gameDirectory = layout.projectDirectory.dir("run")
        }
        register("server") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-server")
            programArgument("--nogui")
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Third-party mods are compiled against from libs/ and loaded at runtime from run/mods, so that jar-in-jar
// dependencies (such as Quark's bundled Biolith) resolve exactly as they do in a real instance.
dependencies {
    compileOnly(fileTree("libs") { include("*.jar") })
}

val runtimeMods = listOf(
    "backpacked-neoforge", "framework-neoforge", "Quark-", "Zeta-",
    "curios-neoforge", "accessories-neoforge", "accessories_compat_layer", "owo-lib",
    "FarmersDelight", "mealmastery", "jei-"
)
val clientOnlyMods = listOf("jei-")

val syncClientMods = tasks.register<Sync>("syncClientMods") {
    group = "mod development"
    description = "Copies the dependency jars from libs/ into run/mods."
    from(fileTree("libs") { runtimeMods.forEach { include("$it*.jar") } })
    into(layout.projectDirectory.dir("run/mods"))
}

val syncServerMods = tasks.register<Sync>("syncServerMods") {
    group = "mod development"
    description = "Copies the dependency jars from libs/ into run-server/mods."
    from(fileTree("libs") {
        runtimeMods.forEach { include("$it*.jar") }
        clientOnlyMods.forEach { exclude("$it*.jar") }
    })
    into(layout.projectDirectory.dir("run-server/mods"))
}

tasks.named("runClient") { dependsOn(syncClientMods) }
tasks.named("runServer") { dependsOn(syncServerMods) }

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
