package cz.bedla.revolut.context

import cz.bedla.revolut.Database
import cz.bedla.revolut.service.AccountDao
import cz.bedla.revolut.service.WaitingRoomDao
import cz.bedla.revolut.service.WaitingRoomService
import cz.bedla.revolut.tx.Transactional

interface ApplicationContext {
    fun waitingRoomServiceBean(): WaitingRoomService
    fun waitingRoomDaoBean(): WaitingRoomDao
    fun accountDaoBean(): AccountDao
    fun transactionalBean(): Transactional
    fun databaseBean(): Database

    fun start()
    fun stop()
}
