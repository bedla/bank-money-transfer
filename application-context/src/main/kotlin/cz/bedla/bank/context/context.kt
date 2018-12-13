package cz.bedla.bank.context

import cz.bedla.bank.Database
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.AccountService
import cz.bedla.bank.service.WaitingRoomDao
import cz.bedla.bank.service.WaitingRoomService
import cz.bedla.bank.tx.Transactional

interface ApplicationContext {
    fun waitingRoomServiceBean(): WaitingRoomService
    fun waitingRoomDaoBean(): WaitingRoomDao
    fun accountServiceBean(): AccountService
    fun accountDaoBean(): AccountDao
    fun transactionalBean(): Transactional
    fun databaseBean(): Database

    fun start()
    fun stop()
}
