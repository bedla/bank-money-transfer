package cz.bedla.bank.service.impl

import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.createDsl
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.jooq.tables.Account.ACCOUNT
import org.jooq.Record


class AccountDaoImpl() : AccountDao {
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
        return record?.toAccount()
    }

    override fun findAccounts(): List<Account> {
        val dsl = createDsl()
        val result = dsl.selectFrom(ACCOUNT).orderBy(ACCOUNT.NAME).fetch()
        return result.map { it.toAccount() }
    }

    override fun findAccountsOfType(type: AccountType): List<Account> {
        val dsl = createDsl()
        val result = dsl.selectFrom(ACCOUNT)
                .where(ACCOUNT.TYPE.eq(type.name))
                .orderBy(ACCOUNT.NAME).fetch()
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
