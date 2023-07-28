import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.DockerCommandStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2019_2.projectFeatures.githubIssues
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2022.10"

project {

    // buildType(Build)
    buildType(BuildSecondaryBranches)
    buildType(PullRequests)
    // buildType(DockerBuild)

    params {
        text("git_main_branch", "main", label = "Git Main Branch", description = "The git main or default branch to use in VCS operations.", display = ParameterDisplay.HIDDEN, allowEmpty = false)
        text("github_repository_name", "WhatAmIForgingUp", label = "The github repository name. Used to connect to it in VCS Roots.", description = "This is the repository slug on github. So for example `WhatAmIForgingUp` or `MinecraftForge`. It is interpolated into the global VCS Roots.", display = ParameterDisplay.HIDDEN, allowEmpty = false)
        text("env.PUBLISHED_JAVA_ARTIFACT_ID", "waifu", label = "Published artifact id", description = "The maven coordinate artifact id that has been published by this build. Can not be empty.", allowEmpty = false)
        text("env.PUBLISHED_JAVA_GROUP", "net.neoforged", label = "Published group", description = "The maven coordinate group that has been published by this build. Can not be empty.", allowEmpty = false)
        text("docker_jdk_version", "17", label = "JDK version", description = "The version of the JDK to use during execution of tasks in a JDK.", display = ParameterDisplay.HIDDEN, allowEmpty = false)
        text("docker_gradle_version", "8.1.1", label = "Gradle version", description = "The version of Gradle to use during execution of Gradle tasks.", display = ParameterDisplay.HIDDEN, allowEmpty = false)
    }

    features {
        githubIssues {
            id = "WhatAmIForgingUp__IssueTracker"
            displayName = "MinecraftForge/WhatAmIForgingUp"
            repositoryURL = "https://github.com/MinecraftForge/WhatAmIForgingUp"
        }
    }
}

/* object Build : BuildType({
    templates(AbsoluteId("MinecraftForge_SetupGradleUtilsCiEnvironmen"), AbsoluteId("MinecraftForge_BuildWithDiscordNotifications"), AbsoluteId("MinecraftForge_BuildMainBranches"), AbsoluteId("MinecraftForge_BuildUsingGradle"), AbsoluteId("MinecraftForge_PublishProjectUsingGradle"), AbsoluteId("MinecraftForge_TriggersStaticFilesWebpageGenerator"))
    id("WhatAmIForgingUp__Build")
    name = "Build"
    description = "Builds and Publishes the main branches of the project."
}) */

object BuildSecondaryBranches : BuildType({
    templates(AbsoluteId("MinecraftForge_ExcludesBuildingDefaultBranch"), AbsoluteId("MinecraftForge_SetupGradleUtilsCiEnvironmen"), AbsoluteId("MinecraftForge_BuildWithDiscordNotifications"), AbsoluteId("MinecraftForge_BuildMainBranches"), AbsoluteId("MinecraftForge_BuildUsingGradle"))
    id("WhatAmIForgingUp__BuildSecondaryBranches")
    name = "Build - Secondary Branches"
    description = "Builds and Publishes the secondary branches of the project."
})

object PullRequests : BuildType({
    templates(AbsoluteId("MinecraftForge_BuildPullRequests"), AbsoluteId("MinecraftForge_SetupGradleUtilsCiEnvironmen"), AbsoluteId("MinecraftForge_BuildWithDiscordNotifications"), AbsoluteId("MinecraftForge_BuildUsingGradle"))
    id("WhatAmIForgingUp__PullRequests")
    name = "Pull Requests"
    description = "Builds pull requests for the project"
})

/* object DockerBuild : BuildType({
    id("WhatAmIForgingUp__DockerBuild")
    name = "DockerBuild"

    vcs {
        root(AbsoluteId("MinecraftForge_RepositoryAllBranches"))
    }

    triggers {
        vcs {
            branchFilter = "+:*"
        }
    }

    params {
        text("git_branch_spec", """
                +:refs/heads/(%git_main_branch%)
                +:refs/heads/(main*)
                +:refs/heads/(master*)
                +:refs/heads/(develop|release|staging|main|master)
            """.trimIndent(), label = "The branch specification of the repository", description = "By default all main branches are build by the configuration. Modify this value to adapt the branches build.", display = ParameterDisplay.HIDDEN, allowEmpty = true)
    }

    steps {
        gradle {
            name = "Build JAR"
            tasks = ":configureTeamCity :buildBot"
            dockerImage = "gradle:%docker_gradle_version%-jdk%docker_jdk_version%"
            dockerRunParameters = """
                -v "/opt/cache/agent/gradle:/home/gradle/.gradle" 
                -v "/opt/cache/shared/gradle:/home/gradle/rocache:ro"
                --network=host
                -u 1000:1000
            """.trimIndent()
        }

        dockerCommand {
            name = "Build Image"
            commandType = build {
                source = file {
                    path = "Dockerfile"
                }
                contextDir = "."
                platform = DockerCommandStep.ImagePlatform.Linux
                namesAndTags = """
                    ghcr.io/neoforged/whatamiforgingup:latest
                    ghcr.io/neoforged/whatamiforgingup:%build.number%
                """.trimIndent()
                commandArgs = "--pull"
            }
        }
        dockerCommand {
            name = "Push Image"
            commandType = push {
                namesAndTags = """
                    ghcr.io/neoforged/whatamiforgingup:latest
                    ghcr.io/neoforged/whatamiforgingup:%build.number%
                """.trimIndent()
            }
        }
    }

    features {
        dockerSupport {
            loginToRegistry = on {
                dockerRegistryId = "PROJECT_EXT_7"
            }
        }
    }
}) */