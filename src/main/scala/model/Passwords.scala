package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import doobie.{TableDefinition, Column}
import doobie.implicits.javatimedrivernative._

case class Password(
    username: String,
    secret: String,
    accountId: Int,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime
)

object PasswordsTable extends TableDefinition("passwords"):
    val username: Column[String] = Column("username")
    val secret: Column[String] = Column("secret")
    val accountId: Column[Int] = Column("account_id")
    val createdAt: Column[LocalDateTime] = Column("created_at")
    val updatedAt: Column[LocalDateTime] = Column("updated_at")
