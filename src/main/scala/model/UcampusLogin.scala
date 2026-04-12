package cl.cadcc.ramitos.model

import cl.cadcc.ramitos.model.Password.Table.all

import java.time.{Instant, LocalDateTime}
import doobie.{Column, Columns, Composite, TableDefinition, WithSQLDefinition}
import doobie.implicits.javatimedrivernative.*
import doobie.util.{Write, Read}

case class UcampusLogin(
    ucampusUsername: String,
    accountId: Int,
    createdAt: Instant
) derives Write, Read

object UcampusLogin {
    object Table extends TableDefinition("ucampus_sso") {
        lazy val columns = Columns(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

        val ucampus_id: Column[String] = Column("ucampus_id")
        val account_id: Column[Int] = Column("account_id")
        val created_at: Column[Instant] = Column("created_at")

        object all extends WithSQLDefinition[UcampusLogin](Composite((
            ucampus_id.sqlDef,
            account_id.sqlDef,
            created_at.sqlDef
        ))(UcampusLogin.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[UcampusLogin](this)
    }
}
