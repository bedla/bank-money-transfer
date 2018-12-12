package cz.bedla.revolut.service

import cz.bedla.revolut.domain.Account
import cz.bedla.revolut.domain.WaitingRoom
import cz.bedla.revolut.domain.WaitingRoomState

interface WaitingRoomDao : Dao {
    fun create(item: WaitingRoom): WaitingRoom

    fun findWaitingRoom(id: Int): WaitingRoom?

    fun findItemsWithState(state: WaitingRoomState): List<WaitingRoom>

    fun findItemsForAccount(account: Account): List<WaitingRoom>

    fun updateState(waitingRoom: WaitingRoom)

    fun delete(item: WaitingRoom)
}
