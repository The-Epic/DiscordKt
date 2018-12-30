package me.aberrantfox.kjdautils.api

import com.google.common.eventbus.Subscribe
import me.aberrantfox.kjdautils.api.dsl.*
import me.aberrantfox.kjdautils.internal.command.*
import me.aberrantfox.kjdautils.internal.di.DIService
import me.aberrantfox.kjdautils.internal.event.EventRegister
import me.aberrantfox.kjdautils.internal.listeners.CommandListener
import me.aberrantfox.kjdautils.internal.listeners.ConversationListener
import me.aberrantfox.kjdautils.internal.logging.BotLogger
import me.aberrantfox.kjdautils.internal.logging.DefaultLogger
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDABuilder
import org.reflections.Reflections
import org.reflections.scanners.MethodAnnotationsScanner


class KUtils(val config: KJDAConfiguration) {
    val jda = JDABuilder(AccountType.BOT).setToken(config.token).buildBlocking()

    private var listener: CommandListener? = null
    private var executor: CommandExecutor? = null
    private val helpService: HelpService
    private val diService = DIService()

    val conversationService: ConversationService = ConversationService(jda, config, diService)
    val container = CommandsContainer()
    var logger: BotLogger = DefaultLogger()

    init {
        jda.addEventListener(EventRegister)
        helpService = HelpService(container, config)
        registerListeners(ConversationListener(conversationService))
    }

    fun registerInjectionObject(vararg obj: Any) = obj.forEach { diService.addElement(it) }

    fun registerCommandPreconditions(vararg conditions: (CommandEvent) -> PreconditionResult) = listener?.addPreconditions(*conditions)
    
    fun configure(setup: KJDAConfiguration.() -> Unit) {
        config.setup()

        registerCommands(config.globalPath)
        registerListenersByPath(config.globalPath)
        registerPreconditionsByPath(config.globalPath)
        conversationService.registerConversations(config.globalPath)
    }

    fun registerListeners(vararg listeners: Any) = listeners.forEach { EventRegister.eventBus.register(it) }

    private fun registerCommands(commandPath: String): CommandsContainer {
        val localContainer = produceContainer(config.globalPath, diService)
        CommandRecommender.addAll(localContainer.listCommands())

        val executor = CommandExecutor()
        val listener = CommandListener(config, container, logger, executor)

        this.container.join(localContainer)
        this.executor = executor
        this.listener = listener

        registerListeners(listener)
        return container
    }

    private fun registerListenersByPath(path: String) {
        Reflections(path, MethodAnnotationsScanner()).getMethodsAnnotatedWith(Subscribe::class.java)
                .map { it.declaringClass }
                .distinct()
                .map { diService.invokeConstructor(it) }
                .forEach { registerListeners(it) }
    }

    private fun registerPreconditionsByPath(path: String) {
        Reflections(path, MethodAnnotationsScanner()).getMethodsAnnotatedWith(Precondition::class.java)
                .map { diService.invokeReturningMethod(it) as ((CommandEvent) -> PreconditionResult) }
                .forEach { registerCommandPreconditions(it) }
    }
}

fun startBot(token: String, operate: KUtils.() -> Unit = {}): KUtils {
    val util = KUtils(KJDAConfiguration(token))
    util.operate()
    return util
}