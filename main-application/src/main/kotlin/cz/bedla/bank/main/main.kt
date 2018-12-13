package cz.bedla.bank.main

import cz.bedla.bank.RestServer
import cz.bedla.bank.context.impl.ApplicationContextImpl
import cz.bedla.bank.rest.ApplicationServletContextListener
import cz.bedla.bank.rest.RestApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File

@CommandLine.Command(
    name = "Bank", version = ["0.0.1"],
    mixinStandardHelpOptions = true
)
class BankApplication : Runnable {
    @CommandLine.Option(names = ["-f", "--db-file"], description = ["H2 database file name (full path)"])
    private var dbFile: File = File(".", "bank")

    @CommandLine.Option(names = ["--host"])
    private var host: String = "localhost"

    @CommandLine.Option(names = ["-p", "--port"])
    private var port: Int = 8080

    override fun run() {
        logger.info("Bank starting")
        val servletContextListener = ApplicationServletContextListener(ApplicationContextImpl(dbFile))
        val server = RestServer(
            host, port, servletContextListener, RestApplication::class.java
        ).also { it.start() }
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Bank shutting down")
            server.stop()
        })
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BankApplication::class.java)
    }
}

fun main(args: Array<String>) = CommandLine.run(BankApplication(), *args)
