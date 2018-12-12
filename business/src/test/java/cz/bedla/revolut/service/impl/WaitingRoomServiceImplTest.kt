package cz.bedla.revolut.service.impl

import com.nhaarman.mockitokotlin2.*
import cz.bedla.revolut.domain.Account
import cz.bedla.revolut.domain.AccountType
import cz.bedla.revolut.domain.WaitingRoomState
import cz.bedla.revolut.service.*
import cz.bedla.revolut.tx.TransactionExecuteCallback
import cz.bedla.revolut.tx.TransactionRunCallback
import cz.bedla.revolut.tx.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.AdditionalAnswers
import org.mockito.stubbing.Answer
import java.time.OffsetDateTime

class WaitingRoomServiceImplTest {
    private val transactional = object : Transactional {
        override fun run(action: TransactionRunCallback?) {
            action?.doInTransaction()
        }

        override fun <T : Any?> execute(action: TransactionExecuteCallback<T>?): T? {
            return action?.doInTransaction()
        }
    }

    @Nested
    inner class `Receive Payment request` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
                on { findAccount(eq(456)) } doReturn account(AccountType.PERSONAL, "Mr. Bar")
            }
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { create(any()) } doAnswer returnsFirstArg()
            }
            val fixture = WaitingRoomServiceImpl(waitingRoomDao, accountDao, transactional)

            val waitingRoom = fixture.receivePaymentRequest(123, 456, 999.toBigDecimal())
            assertThat(waitingRoom.fromAccount.name).isEqualTo("Mr. Foo")
            assertThat(waitingRoom.toAccount.name).isEqualTo("Mr. Bar")
            assertThat(waitingRoom.amount).isEqualTo(999.toBigDecimal())
            assertThat(waitingRoom.state).isEqualTo(WaitingRoomState.RECEIVED)

            verify(accountDao).findAccount(eq(123))
            verify(accountDao).findAccount(eq(456))
            verify(waitingRoomDao).create(any())
            verifyNoMoreInteractions(accountDao, waitingRoomDao)
        }


        @Test
        fun `invalid amount`() {
            val fixture = WaitingRoomServiceImpl(mock(), mock(), transactional)
            assertThatThrownBy {
                fixture.receivePaymentRequest(123, 456, 0.toBigDecimal())
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Invalid amount value")

        }

        @Test
        fun `invalid accounts`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn null
                on { findAccount(eq(9123)) } doReturn account(AccountType.PERSONAL)
                on { findAccount(eq(456)) } doReturn null
            }
            val fixture = WaitingRoomServiceImpl(mock(), accountDao, transactional)

            assertThatThrownBy {
                fixture.receivePaymentRequest(123, 456, 999.toBigDecimal())
            }.isInstanceOf(AccountNotFound::class.java)
                .hasMessage("Unable to find account.id=123")

            assertThatThrownBy {
                fixture.receivePaymentRequest(9123, 456, 999.toBigDecimal())
            }.isInstanceOf(AccountNotFound::class.java)
                .hasMessage("Unable to find account.id=456")
        }

        @Test
        fun `not a personal accounts`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.TOP_UP)
                on { findAccount(eq(456)) } doReturn account(AccountType.PERSONAL)
            }
            val fixture = WaitingRoomServiceImpl(mock(), accountDao, transactional)

            assertThatThrownBy {
                fixture.receivePaymentRequest(123, 456, 999.toBigDecimal())
            }.isInstanceOf(InvalidPaymentRequest::class.java)
                .hasMessage("Invalid payment request from account.id=123 to account.id=456")

            assertThatThrownBy {
                fixture.receivePaymentRequest(456, 123, 999.toBigDecimal())
            }.isInstanceOf(InvalidPaymentRequest::class.java)
                .hasMessage("Invalid payment request from account.id=456 to account.id=123")
        }
    }

    @Nested
    inner class `Top-up request` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
                on { findAccountsOfType(eq(AccountType.TOP_UP)) } doReturn listOf(
                    account(
                        AccountType.TOP_UP,
                        "Bank"
                    )
                )
            }
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { create(any()) } doAnswer returnsFirstArg()
            }
            val fixture = WaitingRoomServiceImpl(waitingRoomDao, accountDao, transactional)

            val waitingRoom = fixture.topUpRequest(123, 999.toBigDecimal())
            assertThat(waitingRoom.fromAccount.type).isEqualTo(AccountType.TOP_UP)
            assertThat(waitingRoom.toAccount.type).isEqualTo(AccountType.PERSONAL)
            assertThat(waitingRoom.amount).isEqualTo(999.toBigDecimal())
            assertThat(waitingRoom.state).isEqualTo(WaitingRoomState.RECEIVED)

            verify(accountDao).findAccount(eq(123))
            verify(accountDao).findAccountsOfType(eq(AccountType.TOP_UP))
            verify(waitingRoomDao).create(any())
            verifyNoMoreInteractions(accountDao, waitingRoomDao)
        }

        @Test
        fun `invalid amount`() {
            val fixture = WaitingRoomServiceImpl(mock(), mock(), transactional)
            assertThatThrownBy {
                fixture.topUpRequest(123, 0.toBigDecimal())
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Invalid amount value")

        }

        @Test
        fun `unable to find Top-up account`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
                on { findAccountsOfType(eq(AccountType.TOP_UP)) } doReturn listOf()
            }

            val fixture = WaitingRoomServiceImpl(mock(), accountDao, transactional)
            assertThatThrownBy {
                fixture.topUpRequest(123, 999.toBigDecimal())
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Unable to find any top-up account")
        }

        @Test
        fun `invalid AccountType`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.TOP_UP, "Mr. Invalid")
                on { findAccountsOfType(eq(AccountType.TOP_UP)) } doReturn listOf(
                    account(
                        AccountType.TOP_UP,
                        "Bank"
                    )
                )
            }

            val fixture = WaitingRoomServiceImpl(mock(), accountDao, transactional)
            assertThatThrownBy {
                fixture.topUpRequest(123, 999.toBigDecimal())
            }.isInstanceOf(InvalidTopUpRequest::class.java)
                .hasMessage("Invalid top-up request for account.id=0")
        }
    }

    @Nested
    inner class `Withdrawal request` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
                on { findAccountsOfType(eq(AccountType.WITHDRAWAL)) } doReturn listOf(
                    account(AccountType.WITHDRAWAL, "Bank")
                )
            }
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { create(any()) } doAnswer returnsFirstArg()
            }
            val fixture = WaitingRoomServiceImpl(waitingRoomDao, accountDao, transactional)

            val waitingRoom = fixture.withdrawalRequest(123, 999.toBigDecimal())
            assertThat(waitingRoom.fromAccount.type).isEqualTo(AccountType.PERSONAL)
            assertThat(waitingRoom.toAccount.type).isEqualTo(AccountType.WITHDRAWAL)
            assertThat(waitingRoom.amount).isEqualTo(999.toBigDecimal())
            assertThat(waitingRoom.state).isEqualTo(WaitingRoomState.RECEIVED)

            verify(accountDao).findAccount(eq(123))
            verify(accountDao).findAccountsOfType(eq(AccountType.WITHDRAWAL))
            verify(waitingRoomDao).create(any())
            verifyNoMoreInteractions(accountDao, waitingRoomDao)
        }

        @Test
        fun `invalid amount`() {
            val fixture = WaitingRoomServiceImpl(mock(), mock(), transactional)
            assertThatThrownBy {
                fixture.withdrawalRequest(123, 0.toBigDecimal())
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Invalid amount value")

        }

        @Test
        fun `unable to find Withdrawal account`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
                on { findAccountsOfType(eq(AccountType.WITHDRAWAL)) } doReturn listOf()
            }

            val fixture = WaitingRoomServiceImpl(mock(), accountDao, transactional)
            assertThatThrownBy {
                fixture.withdrawalRequest(123, 999.toBigDecimal())
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessage("Unable to find any withdrawal account")
        }

        @Test
        fun `invalid AccountType`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.WITHDRAWAL, "Mr. Invalid")
                on { findAccountsOfType(eq(AccountType.WITHDRAWAL)) } doReturn listOf(
                    account(AccountType.WITHDRAWAL, "Bank")
                )
            }

            val fixture = WaitingRoomServiceImpl(mock(), accountDao, transactional)
            assertThatThrownBy {
                fixture.withdrawalRequest(123, 999.toBigDecimal())
            }.isInstanceOf(InvalidWithdrawalRequest::class.java)
                .hasMessage("Invalid withdrawal request for account.id=0")
        }
    }

    @Nested
    inner class `List WaitingRoom requests for personal accounts` {
        @Test
        fun `business`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
            }
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { findItemsForAccount(any()) } doReturn listOf()
            }

            val fixture = WaitingRoomServiceImpl(waitingRoomDao, accountDao, transactional)
            fixture.listWaitingRoomRequestsForPersonalAccounts(123)

            verify(accountDao).findAccount(eq(123))
            verify(waitingRoomDao).create(any())
            verifyNoMoreInteractions(accountDao, waitingRoomDao)
        }

        @Test
        fun `invalid AccountType`() {
            val accountDao = mock<AccountDao> {
                on { findAccount(eq(123)) } doReturn account(AccountType.WITHDRAWAL, "Mr. Invalid")
            }

            val fixture = WaitingRoomServiceImpl(mock(), accountDao, transactional)
            assertThatThrownBy {
                fixture.listWaitingRoomRequestsForPersonalAccounts(123)
            }.isInstanceOf(InvalidAccountRequest::class.java)
                .hasMessage("Invalid list request for account.id=0")
        }
    }

    private fun account(accountType: AccountType, name: String = "foo") =
        Account(accountType, name, OffsetDateTime.now(), 0.toBigDecimal())

    private fun returnsFirstArg(): Answer<Any> {
        return AdditionalAnswers.returnsFirstArg()
    }
}
