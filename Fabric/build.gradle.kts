import net.darkhax.curseforgegradle.TaskPublishCurseForge
import net.darkhax.curseforgegradle.Constants as CFG_Constants

plugins {
	java
	id("fabric-loom") version ("0.11-SNAPSHOT")
	id("net.darkhax.curseforgegradle") version("1.0.8")
}

// gradle.properties
val curseHomepageLink: String by extra
val curseProjectId: String by extra
val fabricVersion: String by extra
val fabricLoaderVersion: String by extra
val mappingsChannel: String by extra
val mappingsVersion: String by extra
val minecraftVersion: String by extra
val modId: String by extra
val modFileName: String by extra
val modJavaVersion: String by extra

val baseArchivesName = "${modFileName}-Fabric-${minecraftVersion}"
base {
	archivesName.set(baseArchivesName)
}

val dependencyProjects: List<Project> = listOf(
	project(":Common"),
	project(":CommonApi"),
	project(":FabricApi"),
)

dependencyProjects.forEach {
	project.evaluationDependsOn(it.path)
}

sourceSets {
}

java {
	withSourcesJar()
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${minecraftVersion}")
	mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricVersion}")
	dependencyProjects.forEach {
		if (it.path.contains("Fabric")) {
			implementation(project(path = it.path, configuration = "namedElements"))
		} else {
			implementation(it)
		}
	}
}

tasks.named<Jar>("jar") {
	from(sourceSets.main.get().output)
	for (p in dependencyProjects) {
		from(p.sourceSets.main.get().output)
	}
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val apiJar = tasks.register<Jar>("apiJar") {
	from(project(":CommonApi").sourceSets.main.get().output)
	from(project(":FabricApi").sourceSets.main.get().output)

	// TODO: when FG bug is fixed, remove allJava from the api jar.
	// https://github.com/MinecraftForge/ForgeGradle/issues/369
	// Gradle should be able to pull them from the -sources jar.
	from(project(":CommonApi").sourceSets.main.get().allJava)
	from(project(":FabricApi").sourceSets.main.get().allJava)

	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveClassifier.set("api")
}

artifacts {
	archives(apiJar.get())
	archives(tasks.jar.get())
	archives(tasks.remapJar.get())
	archives(tasks.remapSourcesJar.get())
}

tasks.withType<Jar> {
	destinationDirectory.set(file(rootProject.rootDir.path + "/output"))
}

tasks.withType<net.fabricmc.loom.task.RemapJarTask> {
	destinationDirectory.set(file(rootProject.rootDir.path + "/output"))
}

loom {
	accessWidenerPath.set(file("../Common/src/main/resources/betteradvancements.accesswidener"))
	runs {
		create("FabricClient") {
			client()
			configName = "Fabric Client"
			ideConfigGenerated(true)
			runDir("run")
		}
		create("FabricServer") {
			server()
			configName = "Fabric Server"
			ideConfigGenerated(true)
			runDir("run")
		}
	}
}

tasks.withType<ProcessResources> {
	from(project(":Common").sourceSets.main.get().resources)
}

tasks.register<TaskPublishCurseForge>("publishCurseForge") {

	apiToken = System.getenv("CURSE_KEY") ?: "0"

	val mainFile = upload(curseProjectId, tasks.remapJar.get())
	mainFile.changelogType = CFG_Constants.CHANGELOG_MARKDOWN
	mainFile.changelog = System.getenv("CHANGELOG") ?: ""
	mainFile.releaseType = CFG_Constants.RELEASE_TYPE_ALPHA
	mainFile.addModLoader("Fabric")
	mainFile.addJavaVersion("Java $modJavaVersion")
	mainFile.addGameVersion(minecraftVersion)
	mainFile.withAdditionalFile(apiJar.get())
	mainFile.withAdditionalFile(tasks.remapJar.get())
	mainFile.withAdditionalFile(tasks.remapSourcesJar.get())
}
