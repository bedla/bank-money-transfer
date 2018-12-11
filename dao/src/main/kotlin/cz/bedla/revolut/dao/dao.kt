package cz.bedla.revolut.dao

import cz.bedla.revolut.tx.Transactional
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL

interface Dao {
}

fun Dao.createDsl(): DSLContext = DSL.using(
        Transactional.currentConnection(),
        SQLDialect.H2,
        Settings().withExecuteWithOptimisticLocking(true))
