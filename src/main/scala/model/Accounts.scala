package cl.cadcc.ramitos.model

import cats.syntax.all.*
import java.time.LocalDateTime
import doobie.{Column, Columns, Composite, Meta, TableDefinition, WithSQLDefinition}
import doobie.postgres.implicits.*
import cl.cadcc.ramitos.schema.{Account as SchemaAccount, AccountRole as SchemaRole}
import cl.cadcc.ramitos.utils.Shapeless.*
import cl.cadcc.ramitos.utils.extensions.*
import cats.mtl.Local
import io.circe.Codec
import doobie.util.{Get, Put, Read, Write}

import scala.deriving.Mirror
import cats.kernel.Order

enum AccountRole(val s: String, val order: Int) derives Codec:
    case NONE extends AccountRole("none", 0)
    case STATS extends AccountRole("stats", 1)
    case MOD extends AccountRole("mod", 2)
    case ADMIN extends AccountRole("admin", 3)


object AccountRole {
    given Meta[AccountRole] = pgEnumStringOpt[AccountRole]("account_role", AccountRole.ofString, _.s)

    def ofString(s: String) = s match {
        case "none" => AccountRole.NONE.some
        case "stats" => AccountRole.STATS.some
        case "mod" => AccountRole.MOD.some
        case "admin" => AccountRole.ADMIN.some
        case _ => None
    }
    
    given Ordering[AccountRole] = Ordering[Int].on[AccountRole](_.order)
    given Order[AccountRole] = Order.by(_.order)
}

given roleConvert: SchemaModelConvert[AccountRole, SchemaRole] = SchemaModelConvert.instance {
    case AccountRole.NONE => SchemaRole.NONE
    case AccountRole.STATS => SchemaRole.STATS
    case AccountRole.MOD => SchemaRole.MOD
    case AccountRole.ADMIN => SchemaRole.ADMIN
}

case class Account(
        id: Int,
        displayName: String,
        /// Normalized to no leading zeros, no dots, no verifier digit.
        mufasaId: Option[String],
        role: AccountRole,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime
    ) derives Codec, Read, Write

given accountConvert: SchemaModelConvert[Account, SchemaAccount] = SchemaModelConvert.instance { acc =>
    SchemaAccount(
        id = acc.id,
        name = acc.displayName,
        mufasaId = acc.mufasaId,
        role = acc.role.toSchema,
        created_at = acc.createdAt.toSchema,
        updated_at = acc.updatedAt.toSchema,
    )
}

object Account {

    object Table extends TableDefinition("accounts") {
        lazy val columns = Columns(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

        val id: Column[Int] = Column("id")
        val displayName: Column[String] = Column("name")
        val mufasaId: Column[Option[String]] = Column("mufasa_id")
        val role: Column[AccountRole] = Column("role")
        val createdAt: Column[LocalDateTime] = Column("created_at")
        val updatedAt: Column[LocalDateTime] = Column("updated_at")

        object all extends WithSQLDefinition[Account](Composite((
            id.sqlDef,
            displayName.sqlDef,
            mufasaId.sqlDef,
            role.sqlDef,
            createdAt.sqlDef,
            updatedAt.sqlDef
        ))(Account.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[Account](this)
    }
}
