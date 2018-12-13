package cz.bedla.bank.service.impl

import com.nhaarman.mockitokotlin2.*
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.AccountNotFound
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class AccountServiceImplTest {
    @Nested
    inner class `Create Personal account` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { createAccount(any()) } doAnswer returnsFirstArg()
            }
            val fixture = AccountServiceImpl(accountDao, transactional)

            val account = fixture.createPersonalAccount("Mr. Foo")
            assertThat(account.type).isEqualTo(AccountType.PERSONAL)
            assertThat(account.name).isEqualTo("Mr. Foo")
            assertThat(account.balance).isEqualTo(0.toBigDecimal())

            verify(accountDao).createAccount(any())
            verifyNoMoreInteractions(accountDao)
        }

        @Test
        fun `invalid name`() {
            val fixture = AccountServiceImpl(mock(), transactional)
            assertThatThrownBy {
                fixture.createPersonalAccount("")
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Account name cannot be empty")

        }
    }

    @Nested
    inner class `Create Top-up account` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { createAccount(any()) } doAnswer returnsFirstArg()
            }
            val fixture = AccountServiceImpl(accountDao, transactional)

            val account = fixture.createTopUpAccount("Bank top-up", 999.toBigDecimal())
            assertThat(account.type).isEqualTo(AccountType.TOP_UP)
            assertThat(account.name).isEqualTo("Bank top-up")
            assertThat(account.balance).isEqualTo(999.toBigDecimal())

            verify(accountDao).createAccount(any())
            verifyNoMoreInteractions(accountDao)
        }

        @Test
        fun `invalid name`() {
            val fixture = AccountServiceImpl(mock(), transactional)
            assertThatThrownBy {
                fixture.createTopUpAccount("", 0.toBigDecimal())
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Account name cannot be empty")

        }
    }

    @Nested
    inner class `Create Withdrawal account` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { createAccount(any()) } doAnswer returnsFirstArg()
            }
            val fixture = AccountServiceImpl(accountDao, transactional)

            val account = fixture.createWithdrawalAccount("Bank withdrawal", 999.toBigDecimal())
            assertThat(account.type).isEqualTo(AccountType.WITHDRAWAL)
            assertThat(account.name).isEqualTo("Bank withdrawal")
            assertThat(account.balance).isEqualTo(999.toBigDecimal())

            verify(accountDao).createAccount(any())
            verifyNoMoreInteractions(accountDao)
        }

        @Test
        fun `invalid name`() {
            val fixture = AccountServiceImpl(mock(), transactional)
            assertThatThrownBy {
                fixture.createWithdrawalAccount("", 0.toBigDecimal())
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Account name cannot be empty")

        }
    }

    @Nested
    inner class `Find account` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL)
            }
            val fixture = AccountServiceImpl(accountDao, transactional)

            val account = fixture.findAccount(123)
            assertThat(account.name).isEqualTo("Mr. PERSONAL Account")

            verify(accountDao).findAccount(eq(123))
            verifyNoMoreInteractions(accountDao)
        }

        @Test
        fun `not found`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn null
            }
            val fixture = AccountServiceImpl(accountDao, transactional)

            assertThatThrownBy {
                fixture.findAccount(123)
            }.isInstanceOf(AccountNotFound::class.java)
                .hasMessage("Unable to find account.id=123")
        }

    }

    @Nested
    inner class `Find top-up account` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { findAccountsOfType(AccountType.TOP_UP) } doReturn listOf(
                    account(AccountType.TOP_UP, 1000),
                    account(AccountType.TOP_UP, 2000)
                )
            }
            val fixture = AccountServiceImpl(accountDao, transactional)

            val account = fixture.findTopUpAccount()
            assertThat(account.name).isEqualTo("Mr. TOP_UP Account")
            assertThat(account.balance).isEqualTo(1000.toBigDecimal())

            verify(accountDao).findAccountsOfType(AccountType.TOP_UP)
            verifyNoMoreInteractions(accountDao)
        }

        @Test
        fun `not found`() {
            val accountDao = mock<AccountDao> {
                on { findAccountsOfType(AccountType.TOP_UP) } doReturn listOf()
            }
            val fixture = AccountServiceImpl(accountDao, transactional)

            assertThatThrownBy {
                fixture.findTopUpAccount()
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Unable to find any top-up account")
        }

    }

    @Nested
    inner class `Find withdrawal account` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { findAccountsOfType(AccountType.WITHDRAWAL) } doReturn listOf(
                    account(AccountType.WITHDRAWAL, 1000),
                    account(AccountType.WITHDRAWAL, 2000)
                )
            }
            val fixture = AccountServiceImpl(accountDao, transactional)

            val account = fixture.findWithdrawalAccount()
            assertThat(account.name).isEqualTo("Mr. WITHDRAWAL Account")
            assertThat(account.balance).isEqualTo(2000.toBigDecimal())

            verify(accountDao).findAccountsOfType(AccountType.WITHDRAWAL)
            verifyNoMoreInteractions(accountDao)
        }

        @Test
        fun `not found`() {
            val accountDao = mock<AccountDao> {
                on { findAccountsOfType(AccountType.WITHDRAWAL) } doReturn listOf()
            }
            val fixture = AccountServiceImpl(accountDao, transactional)

            assertThatThrownBy {
                fixture.findWithdrawalAccount()
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Unable to find any withdrawal account")
        }

    }

    private fun account(type: AccountType, balance: Int = 0) =
        Account(type, "Mr. $type Account", OffsetDateTime.now(), balance.toBigDecimal())
}
