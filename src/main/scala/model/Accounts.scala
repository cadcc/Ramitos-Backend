package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import doobie.{TableDefinition, Column}
import doobie.implicits.javatimedrivernative._
import cats.mtl.Local
import io.circe.Codec
import doobie.util.{Get, Put, Read, Write}

enum AccountRole derives Codec:
    case none, stats, mod, admin

object AccountRole:
    given Get[AccountRole] = Get.deriveEnumString[AccountRole]
    given Put[AccountRole] = Put.deriveEnumString[AccountRole]

case class Account(
        id: Int,
        displayName: String,
        role: AccountRole,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime
    ) derives Codec, Read, Write:
    val t = AccountsTable

protected object AccountsTable extends TableDefinition("accounts"):
    val id: Column[Int] = Column("id")
    val displayName: Column[String] = Column("name")
    val role: Column[AccountRole] = Column("role")
    val createdAt: Column[LocalDateTime] = Column("created_at")
    val updatedAt: Column[LocalDateTime] = Column("updated_at")
