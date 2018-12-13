package cz.bedla.bank.service.impl

import cz.bedla.bank.service.AccountService
import cz.bedla.bank.service.BankInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BankInitializerImpl(private val accountService: AccountService) : BankInitializer {
    override fun init() {
        initTopUpAccount()
        initWithdrawalAccount()
    }

    private fun initTopUpAccount() {
        try {
            accountService.findTopUpAccount()
        } catch (e: IllegalStateException) {
            logger.info("Initializing top-up account")
            accountService.createTopUpAccount("Bank top-up account", 999999.toBigDecimal())
        }
    }

    private fun initWithdrawalAccount() {
        try {
            accountService.findWithdrawalAccount()
        } catch (e: IllegalStateException) {
            logger.info("Initializing withdrawal account")
            accountService.createWithdrawalAccount("Bank withdrawal account", 999999.toBigDecimal())
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BankInitializerImpl::class.java)
    }
}
