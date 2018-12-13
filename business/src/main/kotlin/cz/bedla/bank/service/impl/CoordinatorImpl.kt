package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.WaitingRoom
import cz.bedla.bank.service.Coordinator
import cz.bedla.bank.service.Transactor
import cz.bedla.bank.service.WaitingRoomService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class CoordinatorImpl(
    numWorkers: Int,
    private val periodSeconds: Int,
    private val waitingRoomService: WaitingRoomService,
    private val transactor: Transactor,
    private val poller: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val workerExecutor: ExecutorService = Executors.newFixedThreadPool(numWorkers)
) : Coordinator {

    override fun start() {
        logger.info("Coordinator starting")
        poller.scheduleAtFixedRate(createPoller(), 5, periodSeconds.toLong(), TimeUnit.SECONDS)
    }

    private fun createPoller(): WaitingRoomPoller {
        return WaitingRoomPoller(waitingRoomService) { waitingRoom ->
            workerExecutor.submit { transactor.process(waitingRoom) }
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

    class WaitingRoomPoller(
        private val waitingRoomService: WaitingRoomService,
        private val processAction: (WaitingRoom) -> Unit
    ) : Runnable {
        override fun run() {
            logger.info("Polling for new waiting-room requests")
            val list = waitingRoomService.listWaitingRoomsToProcess()
            logger.info("Found ${list.size} potential requests to process")
            for (waitingRoom in list) {
                processAction(waitingRoom)
            }
        }

        companion object {
            private val logger: Logger = LoggerFactory.getLogger(WaitingRoomPoller::class.java)
        }
    }
}
