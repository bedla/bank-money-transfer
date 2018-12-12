package cz.bedla.bank.rest

import cz.bedla.bank.context.ApplicationContext
import javax.servlet.ServletContext
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener

internal interface Endpoint {
    val servletContext: ServletContext
}

internal fun Endpoint.applicationContext(): ApplicationContext =
    servletContext.getAttribute(ApplicationServletContextListener.APPLICATION) as ApplicationContext

class ApplicationServletContextListener(
    private val applicationContext: ApplicationContext
) : ServletContextListener {

    private lateinit var servletContext: ServletContext

    override fun contextInitialized(sce: ServletContextEvent?) {
        servletContext = sce?.servletContext ?: error("No servlet-context available")
        servletContext.setAttribute(APPLICATION, applicationContext)

        findApplicationContext().start()
    }

    override fun contextDestroyed(sce: ServletContextEvent?) {
        findApplicationContext().stop()
    }

    fun findApplicationContext(): ApplicationContext = servletContext.getAttribute(APPLICATION) as ApplicationContext

    companion object {
        val APPLICATION = "bank.application"
    }
}
