package me.jakejmattson.discordkt.internal.services

import me.jakejmattson.discordkt.api.arguments.AnyArg
import me.jakejmattson.discordkt.api.dsl.command.*
import me.jakejmattson.discordkt.internal.utils.Recommender
import java.awt.Color

internal fun produceHelpCommand(embedColor: Color) = commands {
    command("Help") {
        description = "Display a help menu."
        category = "Utility"
        execute(AnyArg("Command").makeOptional("")) { event ->
            val query = event.args.first
            val commands = event.discord.commands

            when {
                query.isEmpty() -> sendDefaultEmbed(event, embedColor)
                query.isCommand(event) -> sendCommandEmbed(commands[query]!!, event, query, embedColor)
                else -> Recommender.sendRecommendation(event, query, fetchVisibleCommands(event).flatMap { it.names })
            }
        }
    }
}

private fun sendDefaultEmbed(event: CommandEvent<*>, embedColor: Color) =
    event.respond {
        title = "Help menu"
        description = "Use `${event.relevantPrefix}help <command>` for more information."
        color = embedColor

        fetchVisibleCommands(event)
            .groupBy { it.category }
            .toList()
            .sortedBy { (_, commands) -> -commands.size }
            .map { (category, commands) ->
                field {
                    name = category
                    value = "```css\n" +
                        commands
                            .sortedBy { it.names.joinToString() }
                            .joinToString("\n")
                            { it.names.joinToString() } +
                        "\n```"
                    inline = true
                }
            }
    }

private fun sendCommandEmbed(command: Command, event: CommandEvent<*>, input: String, embedColor: Color) =
    event.respond {
        title = command.names.joinToString()
        description = command.description
        color = embedColor

        val commandInvocation = "${event.relevantPrefix}$input"

        field {
            name = "Structure"
            value = "$commandInvocation ${generateStructure(command)}"
        }

        if (command.parameterCount != 0)
            field {
                name = "Examples"
                value = "$commandInvocation ${generateExample(command, event)}"
            }
    }

private fun generateExample(command: Command, event: CommandEvent<*>) =
    command.arguments.joinToString(" ") {
        val examples = it.generateExamples(event)
        val example = if (examples.isNotEmpty()) examples.random() else "<Example>"

        if (it.isOptional) "($example)" else "[$example]"
    }

private fun String.isCommand(event: CommandEvent<*>) = fetchVisibleCommands(event)
    .any { toLowerCase() in it.names.map { it.toLowerCase() } }

private fun fetchVisibleCommands(event: CommandEvent<*>) = event.discord.commands
    .filter { event.discord.configuration.permissions.invoke(it, event.author, event.channel, event.guild) }

private fun generateStructure(command: Command) =
    command.arguments.joinToString(" ") {
        val type = it.name
        if (it.isOptional) "($type)" else "[$type]"
    }
