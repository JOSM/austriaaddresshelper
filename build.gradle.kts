import com.github.spotbugs.SpotBugsTask
import net.ltgt.gradle.errorprone.*
import org.openstreetmap.josm.gradle.plugin.config.I18nConfig
import org.openstreetmap.josm.gradle.plugin.config.JosmManifest
import org.openstreetmap.josm.gradle.plugin.task.MarkdownToHtml
import java.net.URL

plugins {
    id("org.openstreetmap.josm") version "0.6.1"
    id("com.github.spotbugs") version "1.6.4"
    id("net.ltgt.errorprone") version "0.6"
    java
    pmd
    `maven-publish`
}

repositories {
    jcenter()
}

java.sourceCompatibility = JavaVersion.VERSION_1_8
base.archivesBaseName = "austriaaddresshelper"

val junitVersion = "5.3.1"
val errorProneVersion = "2.3.2"
val spotbugsVersion = "3.1.5"
val pmdVersion = "6.16.0"

dependencies {
    implementation("org.apache.logging.log4j:log4j-api:2.12.0")
    implementation("org.apache.logging.log4j:log4j-core:2.12.0")
    testImplementation ("org.openstreetmap.josm:josm-unittest:SNAPSHOT"){ isChanging = true }
    testImplementation("com.github.tomakehurst:wiremock:2.19.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("com.github.spotbugs:spotbugs-annotations:3.1.7")
}

// Set up ErrorProne
dependencies {
    errorprone("com.google.errorprone:error_prone_core:$errorProneVersion")
    if (!JavaVersion.current().isJava9Compatible) {
        errorproneJavac("com.google.errorprone:javac:9+181-r4173-1")
    }
}
tasks.withType(JavaCompile::class).configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
    options.errorprone {
        check("ClassCanBeStatic", CheckSeverity.ERROR)
        check("StringEquality", CheckSeverity.ERROR)
        check("WildcardImport", CheckSeverity.ERROR)
        check("MethodCanBeStatic", CheckSeverity.WARN)
        check("RemoveUnusedImports", CheckSeverity.WARN)
        check("PrivateConstructorForUtilityClass", CheckSeverity.WARN)
        check("LambdaFunctionalInterface", CheckSeverity.WARN)
        check("ConstantField", CheckSeverity.WARN)
    }
}

spotbugs {
    toolVersion = spotbugsVersion
    setIgnoreFailures(true)
    effort = "max"
    reportLevel = "low"
}
pmd {
    toolVersion = pmdVersion
    setIgnoreFailures(true)
    ruleSets("category/java/bestpractices.xml", "category/java/codestyle.xml", "category/java/errorprone.xml")
}

josm {
    i18n {
        pathTransformer = getPathTransformer("github.com/JOSM/austriaaddresshelper/blob")
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
        }
        resources {
            srcDir(project.projectDir).exclude(listOf("src/**")).include(listOf("images/**", "data/*.lang"))
        }
    }
}

tasks.withType(JavaCompile::class) {
  options.encoding = "UTF-8"
}
tasks.withType(SpotBugsTask::class) {
  reports {
    xml.isEnabled = false
    html.isEnabled = true
  }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
