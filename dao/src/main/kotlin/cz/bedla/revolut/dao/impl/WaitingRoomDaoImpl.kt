package cz.bedla.revolut.dao.impl

import cz.bedla.revolut.dao.AccountDao
import cz.bedla.revolut.dao.WaitingRoomDao
import cz.bedla.revolut.dao.createDsl
import cz.bedla.revolut.domain.WaitingRoom
import cz.bedla.revolut.domain.WaitingRoomState
import cz.bedla.revolut.jooq.Tables.WAITING_ROOM
import cz.bedla.revolut.jooq.tables.records.WaitingRoomRecord
import org.jooq.exception.DataChangedException

class WaitingRoomDaoImpl(private val accountDao: AccountDao) : WaitingRoomDao {
    override fun create(item: WaitingRoom): WaitingRoom {
        val dsl = createDsl()

        val waitingRoomRecord = dsl.newRecord(WAITING_ROOM)
        waitingRoomRecord.id = null
        waitingRoomRecord.fromAccId = item.fromAccount.id
        waitingRoomRecord.toAccId = item.toAccount.id
        waitingRoomRecord.amount = item.amount
        waitingRoomRecord.state = item.state.name
        waitingRoomRecord.dateCreated = item.dateCreated
        waitingRoomRecord.version = 0

        waitingRoomRecord.store()

        return item.copy(id = waitingRoomRecord.id, version = waitingRoomRecord.version)
    }

    override fun findWaitingRoom(id: Int): WaitingRoom? {
        val dsl = createDsl()
        val record = dsl.selectFrom(WAITING_ROOM).where(WAITING_ROOM.ID.eq(id)).fetchOne()
        return when (record) {
            null -> null
            else -> record.toWaitingRoom(accountDao)
        }
    }

    override fun delete(item: WaitingRoom) {
        val dsl = createDsl()
        val record = dsl.selectFrom(WAITING_ROOM).where(WAITING_ROOM.ID.eq(item.id)).fetchOne()
        if (record != null) {
            record.set(WAITING_ROOM.VERSION, item.version)
            record.delete()
        } else {
            throw DataChangedException("Record id=${item.id} not found")
        }
    }

    override fun findItemsWithState(state: WaitingRoomState): List<WaitingRoom> {
        val dsl = createDsl()
        return dsl.selectFrom(WAITING_ROOM)
                .where(WAITING_ROOM.STATE.eq(state.name))
                .orderBy(WAITING_ROOM.DATE_CREATED)
                .fetch()
                .map { it.toWaitingRoom(accountDao) }
    }

    override fun updateState(waitingRoom: WaitingRoom) {
        val dsl = createDsl()
        val record = dsl.selectFrom(WAITING_ROOM).where(WAITING_ROOM.ID.eq(waitingRoom.id)).fetchOne()
        record.set(WAITING_ROOM.VERSION, waitingRoom.version)
        record.set(WAITING_ROOM.STATE, waitingRoom.state.name)
        record.store()
    }

    // TODO solve N+1 problem
    private fun WaitingRoomRecord.toWaitingRoom(accountDao: AccountDao): WaitingRoom {
        val id = getValue(WAITING_ROOM.ID)!!
        val fromAccountId = getValue(WAITING_ROOM.FROM_ACC_ID)!!
        val toAccountId = getValue(WAITING_ROOM.TO_ACC_ID)!!
        val fromAccount = accountDao.findAccount(fromAccountId)
                ?: throw IllegalStateException("Unable to find fromAccount.id=$fromAccountId for waitingRoom.id=$id")
        val toAccount = accountDao.findAccount(toAccountId)
                ?: throw IllegalStateException("Unable to find toAccount.id=$toAccountId for waitingRoom.id=$id")

        return WaitingRoom(
                fromAccount,
                toAccount,
                getValue(WAITING_ROOM.AMOUNT),
                WaitingRoomState.valueOf(getValue(WAITING_ROOM.STATE)),
                getValue(WAITING_ROOM.DATE_CREATED),
                getValue(WAITING_ROOM.ID),
                getValue(WAITING_ROOM.VERSION))
    }
}
