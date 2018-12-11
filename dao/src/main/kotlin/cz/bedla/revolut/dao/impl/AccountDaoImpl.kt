package cz.bedla.revolut.dao.impl

import cz.bedla.revolut.dao.AccountDao
import cz.bedla.revolut.dao.DaoDslSupport
import cz.bedla.revolut.domain.Account
import cz.bedla.revolut.domain.AccountType
import cz.bedla.revolut.jooq.tables.Account.ACCOUNT
import org.jooq.Record


class AccountDaoImpl() : AccountDao, DaoDslSupport {
    override fun createAccount(account: Account): Account {
        val dsl = createDsl()

        val accountRecord = dsl.newRecord(ACCOUNT)
        accountRecord.id = null
        accountRecord.type = account.type.name
        accountRecord.name = account.name
        accountRecord.dateOpened = account.dateOpened
        accountRecord.balance = account.balance
        accountRecord.version = 0

        accountRecord.store()

        return account.copy(id = accountRecord.id, version = accountRecord.version)
    }

    override fun updateBalance(account: Account) {
        val dsl = createDsl()
        val record = dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(account.id)).fetchOne()
        record.set(ACCOUNT.VERSION, account.version)
        record.set(ACCOUNT.BALANCE, account.balance)
        record.store()
    }

    override fun findAccount(id: Int): Account? {
        val dsl = createDsl()
        val record = dsl.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchOne()
        return when (record) {
            null -> null
            else -> record.toAccount()
        }
    }

    override fun findAccounts(): List<Account> {
        val dsl = createDsl()
        val result = dsl.selectFrom(ACCOUNT).orderBy(ACCOUNT.NAME).fetch()
        return result.map { it.toAccount() }
    }

    private fun Record.toAccount() = Account(
            AccountType.valueOf(getValue(ACCOUNT.TYPE)),
            getValue(ACCOUNT.NAME),
            getValue(ACCOUNT.DATE_OPENED),
            getValue(ACCOUNT.BALANCE),
            getValue(ACCOUNT.ID),
            getValue(ACCOUNT.VERSION))
}
