package cz.bedla.bank.service.impl

import cz.bedla.bank.Database
import cz.bedla.bank.DatabaseImpl
import cz.bedla.bank.DbInitializer
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.service.AccountService
import cz.bedla.bank.service.BankInitializer
import cz.bedla.bank.tx.TransactionalImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.TempDirectory
import java.nio.file.Path

@ExtendWith(TempDirectory::class)
class BankInitializerImplTest {
    private lateinit var fixture: BankInitializer

    private lateinit var database: Database
    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp(@TempDirectory.TempDir tempDir: Path) {
        database = DatabaseImpl(tempDir.toFile())
        database.start()
        DbInitializer("database.sql", database.dataSource).run()

        accountService = AccountServiceImpl(AccountDaoImpl(), TransactionalImpl(database.dataSource))
        fixture = BankInitializerImpl(accountService)
    }

    @Test
    fun initBank() {
        assertThatThrownBy { accountService.findTopUpAccount() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Unable to find any top-up account")

        assertThatThrownBy { accountService.findWithdrawalAccount() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Unable to find any withdrawal account")

        fixture.init()

        assertThat(accountService.findTopUpAccount().type).isEqualTo(AccountType.TOP_UP)
        assertThat(accountService.findWithdrawalAccount().type).isEqualTo(AccountType.WITHDRAWAL)
    }

    @AfterEach
    internal fun tearDown() {
        database.stop()
    }
}
