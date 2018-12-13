package cz.bedla.bank.service.impl

import cz.bedla.bank.tx.TransactionExecuteCallback
import cz.bedla.bank.tx.TransactionRunCallback
import cz.bedla.bank.tx.Transactional
import org.mockito.AdditionalAnswers
import org.mockito.stubbing.Answer

val transactional = object : Transactional {
    override fun run(action: TransactionRunCallback?) {
        action?.doInTransaction()
    }

    override fun <T : Any?> execute(action: TransactionExecuteCallback<T>?): T? {
        return action?.doInTransaction()
    }
}

fun returnsFirstArg(): Answer<Any> {
    return AdditionalAnswers.returnsFirstArg()
}
