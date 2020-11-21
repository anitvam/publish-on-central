package org.danilopianini.gradle.mavencentral

import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import java.lang.IllegalStateException
import java.net.URI
import java.util.function.BiFunction

inline fun <reified T> Project.propertyWithDefault(default: T): Property<T> =
    objects.property(T::class.java).apply { convention(default) }

fun environmentVariable(name: String) =
    System.getenv(name)
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Environment variable $name is not available")

data class Repository(val name: String, val url: Property<String>, val user: Provider<String>, val password: Provider<String>) {
    override fun toString() = url.get()

    fun configureProject(project: Project) {
        project.extensions.configure(PublishingExtension::class.java) { publishing ->
            publishing.repositories { repository ->
                repository.maven { mavenArtifactRepository ->
                    mavenArtifactRepository.name = name
                    mavenArtifactRepository.url = URI(url.get())
                    mavenArtifactRepository.credentials { credentials ->
                        credentials.username = user.get()
                        credentials.password = password.get()
                        credentials.username
                            ?: PublishOnCentral.logger.warn("No username configured for $name at ${url}.")
                        credentials.password
                            ?: PublishOnCentral.logger.warn("No password configured for $name at ${url}.")
                    }
                }
            }
        }
    }
}

fun Project.mavenCentral() = Repository(
    "MavenCentral",
    url = project.propertyWithDefault("https://oss.sonatype.org/service/local/staging/deploy/maven2/"),
    user = project.providers
        .environmentVariable("MAVEN_CENTRAL_USERNAME")
        .forUseAtConfigurationTime()
        .orElse(project.providers.gradleProperty("mavenCentralUsername")),
    password = project.providers.environmentVariable("MAVEN_CENTRAL_PASSWORD")
        .forUseAtConfigurationTime()
        .orElse(project.providers.gradleProperty("mavenCentralPassword"))
)

internal class PublishOnCentralConfiguration(project: Project) {
    val projectLongName: Property<String> = project.propertyWithDefault(project.name)

    val projectDescription: Property<String> = project.propertyWithDefault("No description provided")

    val licenseName: Property<String> = project.propertyWithDefault("Apache License, Version 2.0")

    val licenseUrl: Property<String> = project.propertyWithDefault("http://www.apache.org/licenses/LICENSE-2.0")

    val scmConnection: Property<String> = project.propertyWithDefault("git:git@github.com:DanySK/${project.name}")

    val projectUrl: Property<String> = project.propertyWithDefault("https://github.com/DanySK/${project.name}")

}

open class PublishOnCentralExtension(val project: Project) {

    internal val configuration = PublishOnCentralConfiguration(project)

    var projectLongName: String
        get() = configuration.projectLongName.get()
        set(value) = configuration.projectLongName.set(value)

    var projectDescription: String
        get() = configuration.projectDescription.get()
        set(value) = configuration.projectDescription.set(value)

    var licenseName: String
        get() = configuration.licenseName.get()
        set(value) = configuration.licenseName.set(value)

    var licenseUrl: String
        get() = configuration.licenseUrl.get()
        set(value) = configuration.licenseUrl.set(value)

    var scmConnection: String
        get() = configuration.scmConnection.get()
        set(value) = configuration.scmConnection.set(value)

    var projectUrl: String
        get() = configuration.projectUrl.get()
        set(value) = configuration.projectUrl.set(value)

    fun repository(url: Property<String>, configurator: MavenRepositoryDescriptor.() -> Unit) {
        val name = extractName.find(url.get())?.destructured?.component1() ?: "unknown"
        val repoDescriptor = MavenRepositoryDescriptor(name, project).apply(configurator)
        Repository(name, url, repoDescriptor.userProperty, repoDescriptor.passwordProperty)
            .configureProject(project)
    }

    fun repository(url: String, configurator: MavenRepositoryDescriptor.() -> Unit) =
        repository(project.propertyWithDefault(url), configurator)

    fun MavenPublication.configurePomForMavenCentral() {
        pom { pom ->
            with(pom) {
                name.set(projectLongName)
                description.set(projectDescription)
                packaging = "jar"
                url.set(projectUrl)
                licenses {
                    it.license { license ->
                        license.name.set(licenseName)
                        license.url.set(licenseUrl)
                    }
                }
                scm { scm ->
                    scm.url.set(projectUrl)
                    scm.connection.set(scmConnection)
                    scm.developerConnection.set(scmConnection)
                }
            }
        }
    }

    companion object {
        val extractName = Regex(
            """.*://(?:\w+\.)*(\w+)\.\w+(?:/.*)?"""
        )
    }
}

class MavenRepositoryDescriptor(var name: String, private val project: Project) {

    internal val userProperty: Property<String> = project.propertyWithDefault("")

    var user: String
        get() = userProperty.get()
        set(value) = userProperty.set(value)

    internal val passwordProperty: Property<String> = project.propertyWithDefault("")

    var password: String
        get() = passwordProperty.get()
        set(value) = passwordProperty.set(value)

    fun user(computeUser: () -> String) = userProperty.set(project.provider(computeUser))

    fun password(computePassword: () -> String) = userProperty.set(project.provider(computePassword))
}