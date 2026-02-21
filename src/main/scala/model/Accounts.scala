package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import doobie.{TableDefinition, Column}
import doobie.implicits.javatimedrivernative._
import cl.cadcc.ramitos.schema.{Account => SchemaAccount, AccountRole => SchemaRole}
import cl.cadcc.ramitos.utils.Shapeless._
import cats.mtl.Local
import io.circe.Codec
import doobie.util.{Get, Put, Read, Write}
import scala.deriving.Mirror
import cats.kernel.Order
import doobie.WithSQLDefinition
import doobie.Composite
import doobie.Columns

enum AccountRole(val s: String, val order: Int) derives Codec:
    case none extends AccountRole("none", 0)
    case stats extends AccountRole("stats", 1)
    case mod extends AccountRole("mod", 2)
    case admin extends AccountRole("admin", 3)

object AccountRole:
    given Get[AccountRole] = Get.deriveEnumString[AccountRole]
    given Put[AccountRole] = Put.deriveEnumString[AccountRole]

given Ordering[AccountRole] = Ordering[Int].on[AccountRole](_.order)
given Order[AccountRole] = Order.by(_.order)

given mish: SchemaModelConvert[AccountRole, SchemaRole] = SchemaModelConvert.instance {
    case AccountRole.none => SchemaRole.NONE
    case AccountRole.stats => SchemaRole.STATS
    case AccountRole.mod => SchemaRole.MOD
    case AccountRole.admin => SchemaRole.ADMIN
}

case class Account(
        id: Int,
        displayName: String,
        role: AccountRole,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime
    ) derives Codec, Read, Write

object Account {

    object Table extends TableDefinition("accounts") {
        lazy val columns = Columns(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

        val id: Column[Int] = Column("id")
        val displayName: Column[String] = Column("name")
        val role: Column[AccountRole] = Column("role")
        val createdAt: Column[LocalDateTime] = Column("created_at")
        val updatedAt: Column[LocalDateTime] = Column("updated_at")

        object all extends WithSQLDefinition[Account](Composite((
            id.sqlDef,
            displayName.sqlDef,
            role.sqlDef,
            createdAt.sqlDef,
            updatedAt.sqlDef
        ))(Account.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[Account](this)
    }
}
