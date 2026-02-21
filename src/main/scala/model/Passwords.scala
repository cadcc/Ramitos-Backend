package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import doobie.{TableDefinition, Column, ConnectionIO}
import doobie.syntax.all._
import doobie.implicits.javatimedrivernative._
import java.util.Optional
import cats.data.NonEmptyVector
import doobie.WithSQLDefinition
import doobie.Composite
import doobie.util.Write
import doobie.util.Read
import doobie.Columns
import scala.collection.mutable.ListBuffer
import doobie.util.fragment.Fragment
import cats.MonadThrow
import cats.syntax.all._

case class Password(
    username: String,
    secret: String,
    accountId: Int,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime
) derives Read, Write

object Password {
    
    object Table extends TableDefinition("passwords") {
        lazy val columns = Columns(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

        val username: Column[String] = Column("username")
        val secret: Column[String] = Column("secret")
        val accountId: Column[Int] = Column("account_id")
        val createdAt: Column[LocalDateTime] = Column("created_at")
        val updatedAt: Column[LocalDateTime] = Column("updated_at")

        object all extends WithSQLDefinition[Password](Composite((
            username.sqlDef,
            secret.sqlDef,
            accountId.sqlDef,
            createdAt.sqlDef,
            updatedAt.sqlDef
        ))(Password.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[Password](this)
    }
}
