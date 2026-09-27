package cl.cadcc.ramitos.model

import cl.cadcc.ramitos.model.Password.Table.all

import java.time.{Instant, LocalDateTime}
import doobie.{Column, Columns, Composite, TableDefinition, WithSQLDefinition}
import doobie.implicits.javatimedrivernative.*
import doobie.util.{Write, Read}

case class DccLogin(
    ucampusId: String,
    mufasaId: String,
    accountId: Int,
    createdAt: Instant
) derives Write, Read

object DccLogin {
    object Table extends TableDefinition("dcc_sso") {
        lazy val columns = Columns(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

        val ucampusId: Column[String] = Column("dcc_id")
        val mufasaId: Column[String] = Column("mufasa_id")
        val accountId: Column[Int] = Column("account_id")
        val createdAt: Column[Instant] = Column("created_at")

        object all extends WithSQLDefinition[DccLogin](Composite((
            ucampusId.sqlDef,
            mufasaId.sqlDef,
            accountId.sqlDef,
            createdAt.sqlDef
        ))(DccLogin.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[DccLogin](this)
    }
}
