package cz.bedla.revolut.main

import cz.bedla.revolut.RestServer
import cz.bedla.revolut.context.impl.ApplicationContextImpl
import cz.bedla.revolut.rest.ApplicationServletContextListener
import cz.bedla.revolut.rest.RestApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File

@CommandLine.Command(
    name = "Bank", version = ["0.0.0"],
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
        logger.info("Bank start")
        val servletContextListener = ApplicationServletContextListener(ApplicationContextImpl(dbFile))
        val server = RestServer(
            host, port, servletContextListener, RestApplication::class.java
        ).also { it.start() }
        Runtime.getRuntime().addShutdownHook(Thread() {
            logger.info("Bank shutdown")
            server.stop()
            servletContextListener.findApplicationContext().stop()
        })
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BankApplication::class.java)
    }
}

fun main(args: Array<String>) = CommandLine.run(BankApplication(), *args)
