package cz.bedla.bank.service

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.WaitingRoom
import cz.bedla.bank.domain.WaitingRoomState

interface WaitingRoomDao : Dao {
    fun create(item: WaitingRoom): WaitingRoom

    fun findWaitingRoom(id: Int): WaitingRoom?

    fun findItemsWithState(state: WaitingRoomState): List<WaitingRoom>

    fun findItemsForAccount(account: Account): List<WaitingRoom>

    fun updateState(waitingRoom: WaitingRoom)

    fun delete(item: WaitingRoom)
}
