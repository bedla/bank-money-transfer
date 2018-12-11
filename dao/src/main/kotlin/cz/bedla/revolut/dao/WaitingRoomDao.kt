package cz.bedla.revolut.dao

import cz.bedla.revolut.domain.WaitingRoom
import cz.bedla.revolut.domain.WaitingRoomState

interface WaitingRoomDao {
    fun create(item: WaitingRoom): WaitingRoom

    fun findWaitingRoom(id: Int): WaitingRoom?

    fun findItemsWithState(state: WaitingRoomState): List<WaitingRoom>

    fun updateState(waitingRoom: WaitingRoom)

    fun delete(item: WaitingRoom)
}
