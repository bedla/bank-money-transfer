package cz.bedla.bank.service.impl

import com.nhaarman.mockitokotlin2.*
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.WaitingRoom
import cz.bedla.bank.domain.WaitingRoomState
import cz.bedla.bank.service.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class WaitingRoomServiceImplTest {
    @Nested
    inner class `Receive Payment request` {
        @Test
        fun `business`() {
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
                on { findAccount(eq(456)) } doReturn account(AccountType.PERSONAL, "Mr. Bar")
            }
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { create(any()) } doAnswer returnsFirstArg()
            }
            val fixture = WaitingRoomServiceImpl(waitingRoomDao, accountService, transactional)

            val waitingRoom = fixture.receivePaymentRequest(123, 456, 999.toBigDecimal())
            assertThat(waitingRoom.fromAccount.name).isEqualTo("Mr. Foo")
            assertThat(waitingRoom.toAccount.name).isEqualTo("Mr. Bar")
            assertThat(waitingRoom.amount).isEqualTo(999.toBigDecimal())
            assertThat(waitingRoom.state).isEqualTo(WaitingRoomState.RECEIVED)

            verify(accountService).findAccount(eq(123))
            verify(accountService).findAccount(eq(456))
            verify(waitingRoomDao).create(any())
            verifyNoMoreInteractions(accountService, waitingRoomDao)
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
        fun `not a personal accounts`() {
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.TOP_UP)
                on { findAccount(eq(456)) } doReturn account(AccountType.PERSONAL)
            }
            val fixture = WaitingRoomServiceImpl(mock(), accountService, transactional)

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
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
                on { findTopUpAccount() } doReturn account(AccountType.TOP_UP, "Bank")
            }
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { create(any()) } doAnswer returnsFirstArg()
            }
            val fixture = WaitingRoomServiceImpl(waitingRoomDao, accountService, transactional)

            val waitingRoom = fixture.topUpRequest(123, 999.toBigDecimal())
            assertThat(waitingRoom.fromAccount.type).isEqualTo(AccountType.TOP_UP)
            assertThat(waitingRoom.toAccount.type).isEqualTo(AccountType.PERSONAL)
            assertThat(waitingRoom.amount).isEqualTo(999.toBigDecimal())
            assertThat(waitingRoom.state).isEqualTo(WaitingRoomState.RECEIVED)

            verify(accountService).findAccount(eq(123))
            verify(accountService).findTopUpAccount()
            verify(waitingRoomDao).create(any())
            verifyNoMoreInteractions(accountService, waitingRoomDao)
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
        fun `invalid AccountType`() {
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.TOP_UP, "Mr. Invalid")
                on { findTopUpAccount() } doReturn account(AccountType.TOP_UP, "Bank")
            }

            val fixture = WaitingRoomServiceImpl(mock(), accountService, transactional)
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
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
                on { findWithdrawalAccount() } doReturn account(AccountType.WITHDRAWAL, "Bank")
            }
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { create(any()) } doAnswer returnsFirstArg()
            }
            val fixture = WaitingRoomServiceImpl(waitingRoomDao, accountService, transactional)

            val waitingRoom = fixture.withdrawalRequest(123, 999.toBigDecimal())
            assertThat(waitingRoom.fromAccount.type).isEqualTo(AccountType.PERSONAL)
            assertThat(waitingRoom.toAccount.type).isEqualTo(AccountType.WITHDRAWAL)
            assertThat(waitingRoom.amount).isEqualTo(999.toBigDecimal())
            assertThat(waitingRoom.state).isEqualTo(WaitingRoomState.RECEIVED)

            verify(accountService).findAccount(eq(123))
            verify(accountService).findWithdrawalAccount()
            verify(waitingRoomDao).create(any())
            verifyNoMoreInteractions(accountService, waitingRoomDao)
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
        fun `invalid AccountType`() {
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.WITHDRAWAL, "Mr. Invalid")
                on { findWithdrawalAccount() } doReturn account(AccountType.WITHDRAWAL, "Bank")
            }

            val fixture = WaitingRoomServiceImpl(mock(), accountService, transactional)
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
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
            }
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { findItemsForAccount(any()) } doReturn listOf()
            }

            val fixture = WaitingRoomServiceImpl(waitingRoomDao, accountService, transactional)
            fixture.listWaitingRoomRequestsForPersonalAccounts(123)

            verify(accountService).findAccount(eq(123))
            verify(waitingRoomDao).findItemsForAccount(any())
            verifyNoMoreInteractions(accountService, waitingRoomDao)
        }

        @Test
        fun `invalid AccountType`() {
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.WITHDRAWAL, "Mr. Invalid")
            }

            val fixture = WaitingRoomServiceImpl(mock(), accountService, transactional)
            assertThatThrownBy {
                fixture.listWaitingRoomRequestsForPersonalAccounts(123)
            }.isInstanceOf(InvalidAccountRequest::class.java)
                .hasMessage("Invalid list request for account.id=0")
        }
    }

    @Nested
    inner class `WaitingRoom state` {
        @Test
        fun `business`() {
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { findWaitingRoom(eq(123)) } doReturn waitingRoom(WaitingRoomState.NO_FUNDS)
            }

            val fixture = WaitingRoomServiceImpl(waitingRoomDao, mock(), transactional)
            assertThat(fixture.waitingRoomState(123)).isEqualTo(WaitingRoomState.NO_FUNDS)

            verify(waitingRoomDao).findWaitingRoom(eq(123))
            verifyNoMoreInteractions(waitingRoomDao)
        }

        @Test
        fun `invalid ID`() {
            val waitingRoomDao = mock<WaitingRoomDao> {
                on { findWaitingRoom(eq(123)) } doReturn null
            }

            val fixture = WaitingRoomServiceImpl(waitingRoomDao, mock(), transactional)
            assertThatThrownBy {
                fixture.waitingRoomState(123)
            }.isInstanceOf(WaitingRoomNotFound::class.java)
                .hasMessage("Unable to find waitingRoom.id=123")
        }
    }

    private fun waitingRoom(state: WaitingRoomState) = WaitingRoom(
        account(AccountType.PERSONAL),
        account(AccountType.PERSONAL),
        0.toBigDecimal(),
        state,
        OffsetDateTime.now()
    )

    private fun account(accountType: AccountType, name: String = "foo") =
        Account(accountType, name, OffsetDateTime.now(), 0.toBigDecimal())
}
