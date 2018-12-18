package cz.bedla.bank.service.impl

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState
import cz.bedla.bank.service.Coordinator
import cz.bedla.bank.service.PaymentOrderService
import cz.bedla.bank.service.Transactor
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CoordinatorImplTest {
    private lateinit var fixture: Coordinator
    private val processed: ConcurrentMap<String, MutableList<PaymentOrder>> =
        ConcurrentHashMap<String, MutableList<PaymentOrder>>()
    private val toProcess = ConcurrentLinkedQueue<List<PaymentOrder>>()
    private val countInvocations = AtomicInteger()

    @BeforeEach
    fun setUp() {
        val paymentOrderService = mock<PaymentOrderService> {
            on { listItemsToProcess() } doAnswer {
                (toProcess.poll() ?: listOf()).also {
                    countInvocations.incrementAndGet()
                }
            }
        }
        val transactor = mock<Transactor> {
            on { process(any()) } doAnswer { invocation ->
                processed.computeIfAbsent(Thread.currentThread().name) { mutableListOf() }
                    .add(invocation.getArgument<PaymentOrder>(0))
                Transactor.ResultState.MONEY_SENT
            }
        }

        fixture = CoordinatorImpl(2, 1, 1, paymentOrderService, transactor)
        fixture.start()
    }

    @Test
    fun processEndToEnd() {
        await().atMost(3, TimeUnit.SECONDS).until { countInvocations.get() > 0 }
        assertThat(processed).isEmpty()

        awaitUntilInvoked {
            toProcess.offer(listOf())
        }
        assertThat(processed).isEmpty()

        toProcess.offer(listOf(paymentOrder()))
        await().until { processed.size == 1 }

        awaitUntilInvoked {
            processed.clear()
        }

        toProcess.offer(listOf(paymentOrder()))
        toProcess.offer(listOf(paymentOrder(), paymentOrder()))
        toProcess.offer(listOf(paymentOrder(), paymentOrder(), paymentOrder()))
        toProcess.offer(listOf(paymentOrder(), paymentOrder(), paymentOrder(), paymentOrder()))
        toProcess.offer(listOf(paymentOrder(), paymentOrder(), paymentOrder(), paymentOrder(), paymentOrder()))
        await().atMost(15 * 2, TimeUnit.SECONDS).until {
            processed.map { it.value.size }.sum() == 15
        }

        assertThat(processed.keys)
            .hasSize(2)
            .allSatisfy {
                assertThat(it)
                    .startsWith("pool-")
                    .contains("-thread-")
            }
    }

    private fun paymentOrder(): PaymentOrder {
        return PaymentOrder(
            Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal()),
            Account(AccountType.PERSONAL, "Mr. Bar", OffsetDateTime.now(), 0.toBigDecimal()),
            100.toBigDecimal(),
            PaymentOrderState.OK,
            OffsetDateTime.now()
        )
    }

    private fun awaitUntilInvoked(block: () -> Unit) {
        val before = countInvocations.get()
        block()
        await().atMost(3, TimeUnit.SECONDS).until { countInvocations.get() > before }
    }

    @AfterEach
    fun tearDown() {
        fixture.stop()
    }
}
