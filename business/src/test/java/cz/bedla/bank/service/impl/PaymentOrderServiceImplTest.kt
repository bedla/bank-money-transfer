package cz.bedla.bank.service.impl

import com.nhaarman.mockitokotlin2.*
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState
import cz.bedla.bank.service.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class PaymentOrderServiceImplTest {
    @Nested
    inner class `Receive Payment request` {
        @Test
        fun `business`() {
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
                on { findAccount(eq(456)) } doReturn account(AccountType.PERSONAL, "Mr. Bar")
            }
            val paymentOrderDao = mock<PaymentOrderDao> {
                on { create(any()) } doAnswer returnsFirstArg()
            }
            val fixture = PaymentOrderServiceImpl(paymentOrderDao, accountService, transactional)

            val paymentOrder = fixture.receivePaymentRequest(123, 456, 999.toBigDecimal())
            assertThat(paymentOrder.fromAccount.name).isEqualTo("Mr. Foo")
            assertThat(paymentOrder.toAccount.name).isEqualTo("Mr. Bar")
            assertThat(paymentOrder.amount).isEqualTo(999.toBigDecimal())
            assertThat(paymentOrder.state).isEqualTo(PaymentOrderState.RECEIVED)

            verify(accountService).findAccount(eq(123))
            verify(accountService).findAccount(eq(456))
            verify(paymentOrderDao).create(any())
            verifyNoMoreInteractions(accountService, paymentOrderDao)
        }


        @Test
        fun `invalid amount`() {
            val fixture = PaymentOrderServiceImpl(mock(), mock(), transactional)
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
            val fixture = PaymentOrderServiceImpl(mock(), accountService, transactional)

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
            val paymentOrderDao = mock<PaymentOrderDao> {
                on { create(any()) } doAnswer returnsFirstArg()
            }
            val fixture = PaymentOrderServiceImpl(paymentOrderDao, accountService, transactional)

            val paymentOrder = fixture.topUpRequest(123, 999.toBigDecimal())
            assertThat(paymentOrder.fromAccount.type).isEqualTo(AccountType.TOP_UP)
            assertThat(paymentOrder.toAccount.type).isEqualTo(AccountType.PERSONAL)
            assertThat(paymentOrder.amount).isEqualTo(999.toBigDecimal())
            assertThat(paymentOrder.state).isEqualTo(PaymentOrderState.RECEIVED)

            verify(accountService).findAccount(eq(123))
            verify(accountService).findTopUpAccount()
            verify(paymentOrderDao).create(any())
            verifyNoMoreInteractions(accountService, paymentOrderDao)
        }

        @Test
        fun `invalid amount`() {
            val fixture = PaymentOrderServiceImpl(mock(), mock(), transactional)
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

            val fixture = PaymentOrderServiceImpl(mock(), accountService, transactional)
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
            val paymentOrderDao = mock<PaymentOrderDao> {
                on { create(any()) } doAnswer returnsFirstArg()
            }
            val fixture = PaymentOrderServiceImpl(paymentOrderDao, accountService, transactional)

            val paymentOrder = fixture.withdrawalRequest(123, 999.toBigDecimal())
            assertThat(paymentOrder.fromAccount.type).isEqualTo(AccountType.PERSONAL)
            assertThat(paymentOrder.toAccount.type).isEqualTo(AccountType.WITHDRAWAL)
            assertThat(paymentOrder.amount).isEqualTo(999.toBigDecimal())
            assertThat(paymentOrder.state).isEqualTo(PaymentOrderState.RECEIVED)

            verify(accountService).findAccount(eq(123))
            verify(accountService).findWithdrawalAccount()
            verify(paymentOrderDao).create(any())
            verifyNoMoreInteractions(accountService, paymentOrderDao)
        }

        @Test
        fun `invalid amount`() {
            val fixture = PaymentOrderServiceImpl(mock(), mock(), transactional)
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

            val fixture = PaymentOrderServiceImpl(mock(), accountService, transactional)
            assertThatThrownBy {
                fixture.withdrawalRequest(123, 999.toBigDecimal())
            }.isInstanceOf(InvalidWithdrawalRequest::class.java)
                .hasMessage("Invalid withdrawal request for account.id=0")
        }
    }

    @Nested
    inner class `List PaymentOrder requests for personal accounts` {
        @Test
        fun `business`() {
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.PERSONAL, "Mr. Foo")
            }
            val paymentOrderDao = mock<PaymentOrderDao> {
                on { findItemsForAccount(any()) } doReturn listOf()
            }

            val fixture = PaymentOrderServiceImpl(paymentOrderDao, accountService, transactional)
            fixture.listItemsForPersonalAccounts(123)

            verify(accountService).findAccount(eq(123))
            verify(paymentOrderDao).findItemsForAccount(any())
            verifyNoMoreInteractions(accountService, paymentOrderDao)
        }

        @Test
        fun `invalid AccountType`() {
            val accountService = mock<AccountService> {
                on { findAccount(eq(123)) } doReturn account(AccountType.WITHDRAWAL, "Mr. Invalid")
            }

            val fixture = PaymentOrderServiceImpl(mock(), accountService, transactional)
            assertThatThrownBy {
                fixture.listItemsForPersonalAccounts(123)
            }.isInstanceOf(InvalidAccountRequest::class.java)
                .hasMessage("Invalid list request for account.id=0")
        }
    }

    @Nested
    inner class `PaymentOrder state` {
        @Test
        fun `business`() {
            val paymentOrderDao = mock<PaymentOrderDao> {
                on { findPaymentOrder(eq(123)) } doReturn paymentOrder(PaymentOrderState.NO_FUNDS)
            }

            val fixture = PaymentOrderServiceImpl(paymentOrderDao, mock(), transactional)
            assertThat(fixture.paymentOrderState(123)).isEqualTo(PaymentOrderState.NO_FUNDS)

            verify(paymentOrderDao).findPaymentOrder(eq(123))
            verifyNoMoreInteractions(paymentOrderDao)
        }

        @Test
        fun `invalid ID`() {
            val paymentOrderDao = mock<PaymentOrderDao> {
                on { findPaymentOrder(eq(123)) } doReturn null
            }

            val fixture = PaymentOrderServiceImpl(paymentOrderDao, mock(), transactional)
            assertThatThrownBy {
                fixture.paymentOrderState(123)
            }.isInstanceOf(PaymentOrderNotFound::class.java)
                .hasMessage("Unable to find paymentOrder.id=123")
        }
    }

    private fun paymentOrder(state: PaymentOrderState) = PaymentOrder(
        account(AccountType.PERSONAL),
        account(AccountType.PERSONAL),
        0.toBigDecimal(),
        state,
        OffsetDateTime.now()
    )

    private fun account(accountType: AccountType, name: String = "foo") =
        Account(accountType, name, OffsetDateTime.now(), 0.toBigDecimal())
}
