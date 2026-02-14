package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import doobie.{TableDefinition, Column}
import doobie.implicits.javatimedrivernative._

object PasswordsTable extends TableDefinition("passwords"):
    val username: Column[String] = Column("username")
    val secret: Column[String] = Column("secret")
    val account_id: Column[Int] = Column("account_id")
    val created_at: Column[LocalDateTime] = Column("created_at")
    val updated_at: Column[LocalDateTime] = Column("updated_at")
