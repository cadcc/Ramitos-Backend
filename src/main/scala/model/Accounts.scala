package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import doobie.{TableDefinition, Column}
import doobie.implicits.javatimedrivernative._

object AccountsTable extends TableDefinition("accounts"):
    val id: Column[Int] = Column("id")
    val displayName: Column[String] = Column("name")
    val createdAt: Column[LocalDateTime] = Column("created_at")
    val updatedAt: Column[LocalDateTime] = Column("updated_at")
