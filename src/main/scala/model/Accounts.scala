package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import doobie.{TableDefinition, Column}
import doobie.implicits.javatimedrivernative._
import cats.mtl.Local
import io.circe.Codec

case class Account(id: Int, displayName: String, createdAt: LocalDateTime, updatedAt: LocalDateTime) derives Codec:
    val t = AccountsTable

protected object AccountsTable extends TableDefinition("accounts"):
    val id: Column[Int] = Column("id")
    val displayName: Column[String] = Column("name")
    val createdAt: Column[LocalDateTime] = Column("created_at")
    val updatedAt: Column[LocalDateTime] = Column("updated_at")
