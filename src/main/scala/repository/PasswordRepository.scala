package cl.cadcc.ramitos.repository

import doobie.syntax.all._, doobie._
import cats._, cats.syntax.all._
import cl.cadcc.ramitos.model.Password
import java.time.LocalDateTime
import scala.collection.mutable.ListBuffer
import cats.data.NonEmptyVector
import scala.language.implicitConversions

object PasswordRepository {
    val Table = Password.Table

    def getByUsername(username: String): ConnectionIO[Option[Password]] =
        sql"SELECT ${Table.columns} FROM $Table WHERE ${Table.username === username}"
            .query[Password]
            .option
    
    def getByAccountId(accountId: Int): ConnectionIO[Option[Password]] =
        sql"SELECT ${Table.columns} FROM $Table WHERE ${Table.accountId === accountId}"
            .query[Password]
            .option

    def updateByUsername(username: String, secret: Option[String], updatedAt: LocalDateTime): ConnectionIO[Password] =
        val upds: ListBuffer[(Fragment, Fragment)] = ListBuffer()
        secret match
            case Some(value) => upds += Table.secret --> value
            case None => ()
        
        if upds.nonEmpty then
            upds += Table.updatedAt --> updatedAt
            sql"${Table.updateTable(NonEmptyVector.fromVectorUnsafe(upds.toVector))} WHERE ${Table.username === username}"
                .update
                .withUniqueGeneratedKeys(Table.columnNames*)
        else
            getByUsername(username).flatMap {
                case Some(value) => value.pure
                case None => MonadThrow[ConnectionIO].raiseError(new EntityNotFoundException(s"No credentials found for username '$username'."))
            }

    def updateByAccountId(accountId: Int, username: Option[String], secret: Option[String], updatedAt: LocalDateTime): ConnectionIO[Password] =
        val upds: ListBuffer[(Fragment, Fragment)] = ListBuffer()

        username match
            case Some(value) => upds += Table.username --> value
            case None => ()

        secret match
            case Some(value) => upds += Table.secret --> value
            case None => ()

        if upds.nonEmpty then
            upds += Table.updatedAt --> updatedAt
            sql"${Table.updateTable(NonEmptyVector.fromVectorUnsafe(upds.toVector))} WHERE ${Table.accountId === accountId}"
                .update
                .withUniqueGeneratedKeys(Table.columnNames*)
        else
            getByAccountId(accountId).flatMap {
                case Some(value) => value.pure
                case None => MonadThrow[ConnectionIO].raiseError(new EntityNotFoundException(s"No credentials found for said accountId '$accountId'."))
            }

    def create(username: String, secret: String, accountId: Int): ConnectionIO[Password] =
        Table.insertInto(NonEmptyVector.of(
            Table.username --> username,
            Table.secret --> secret,
            Table.accountId --> accountId
        )).update
            .withUniqueGeneratedKeys(Table.columnNames*)
}
