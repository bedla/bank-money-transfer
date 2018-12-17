package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.service.Coordinator
import cz.bedla.bank.service.Transactor
import cz.bedla.bank.service.PaymentOrderService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class CoordinatorImpl(
    numWorkers: Int,
    private val initDelaySeconds: Int,
    private val periodSeconds: Int,
    private val paymentOrderService: PaymentOrderService,
    private val transactor: Transactor,
    private val poller: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val workerExecutor: ExecutorService = Executors.newFixedThreadPool(numWorkers)
) : Coordinator {

    override fun start() {
        logger.info("Coordinator starting")
        poller.scheduleAtFixedRate(createPoller(), initDelaySeconds.toLong(), periodSeconds.toLong(), TimeUnit.SECONDS)
    }

    private fun createPoller(): PaymentOrderPoller {
        return PaymentOrderPoller(paymentOrderService) { paymentOrder ->
            workerExecutor.submit { transactor.process(paymentOrder) }
        }
    }

    override fun stop() {
        logger.info("Coordinator stopping")
        workerExecutor.silentlyShutdownAndWait()
        poller.silentlyShutdownAndWait()
    }

    private fun ExecutorService.silentlyShutdownAndWait() {
        try {
            shutdown()
            awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.error("Error while shutting down: $this", e)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CoordinatorImpl::class.java)
    }

    class PaymentOrderPoller(
        private val paymentOrderService: PaymentOrderService,
        private val processAction: (PaymentOrder) -> Unit
    ) : Runnable {
        override fun run() {
            logger.info("Polling for new payment-order requests")
            val list = paymentOrderService.listItemsToProcess()
            logger.info("Found ${list.size} potential requests to process")
            for (paymentOrder in list) {
                processAction(paymentOrder)
            }
        }

        companion object {
            private val logger: Logger = LoggerFactory.getLogger(PaymentOrderPoller::class.java)
        }
    }
}
