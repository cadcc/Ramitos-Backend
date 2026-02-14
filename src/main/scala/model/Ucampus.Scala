package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import doobie.{TableDefinition, Column}
import doobie.implicits.javatimedrivernative._

object UcampusTable extends TableDefinition("ucampus_sso"):
    val ucampus_id: Column[String] = Column("ucampus_id")
    val account_id: Column[Int] = Column("account_id")
    val created_at: Column[LocalDateTime] = Column("created_at")
