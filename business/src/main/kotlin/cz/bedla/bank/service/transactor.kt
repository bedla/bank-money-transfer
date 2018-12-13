package cz.bedla.bank.service

import cz.bedla.bank.domain.WaitingRoom

interface Transactor {
    fun process(waitingRoom: WaitingRoom)
    fun start()
    fun stop()
}
