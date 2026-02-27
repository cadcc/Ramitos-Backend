package cl.cadcc.ramitos.repository

import cats._, cats.data._, cats.implicits._
import doobie._, doobie.implicits._, doobie.syntax.all._
import cl.cadcc.ramitos.model.Account
import cl.cadcc.ramitos.model.AccountRole
import scala.collection.mutable.ListBuffer
import java.time._
import scala.language.implicitConversions

object AccountRepository {
    val Table = Account.Table

    def create(displayName: String, role: AccountRole): ConnectionIO[Account] =
        Table.insertInto(
            Table.displayName --> displayName,
            Table.role --> role
        )
            .update
            .withUniqueGeneratedKeys(Table.columnNames*)

    def update(id: Int, displayName: Option[String] = None, role: Option[AccountRole], updatedAt: Instant): ConnectionIO[Account] =
        var fragList: ListBuffer[(Fragment, Fragment)] = ListBuffer()

        displayName match {
            case Some(value) => fragList += Table.displayName --> value
            case _ => 
        }
        role match {
            case Some(value) => fragList += Table.role --> value
            case _ => 
        }

        if fragList.nonEmpty then
            fragList += Table.updatedAt --> updatedAt.atOffset(ZoneOffset.UTC).toLocalDateTime
            val upds: NonEmptyVector[(Fragment, Fragment)] = NonEmptyVector.fromVectorUnsafe(fragList.toVector)

            sql"${Table.updateTable(upds)} WHERE ${Table.id === id}"
                .update
                .withUniqueGeneratedKeys(Table.columnNames*)
        else
            getById(id).flatMap {
                case None => MonadThrow[ConnectionIO].raiseError(new Exception(""))
                case Some(v) => v.pure
            }

    def getById(id: Int): ConnectionIO[Option[Account]] =
        sql"SELECT ${Table.columns} FROM $Table WHERE ${Table.id === id}"
            .query[Account]
            .option
}
