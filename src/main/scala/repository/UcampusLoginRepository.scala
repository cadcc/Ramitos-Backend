package cl.cadcc.ramitos.repository

import cats.*
import cats.syntax.all.*
import cats.implicits.given
import cats.data.{NonEmptyVector, OptionT}
import cl.cadcc.ramitos.model.{Account, AccountRole, UcampusLogin}
import doobie.free.connection.ConnectionIO
import doobie.syntax.all.*
import doobie.implicits.given

import scala.language.implicitConversions

object UcampusLoginRepository {
    val Table = UcampusLogin.Table

    def getUcampusLogin(ucampusUsername: String): ConnectionIO[Option[UcampusLogin]] =
        sql"SELECT ${Table.columns} FROM $Table WHERE ${Table.ucampus_id === ucampusUsername}"
            .query[UcampusLogin]
            .option

    def create(ucampusUsername: String, accountId: Int): ConnectionIO[UcampusLogin] =
        Table.insertInto(NonEmptyVector.of(
            Table.ucampus_id --> ucampusUsername,
            Table.account_id --> accountId
        )).update
            .withUniqueGeneratedKeys("ucampus_id", "account_id", "created_at")

    def getOrCreateAccount(ucampusUsername: String, mufasaId: String, name: String): ConnectionIO[(Account, UcampusLogin)] =
        for {
            ucampusLogin <- getUcampusLogin(ucampusUsername)
            accLogin <- ucampusLogin match {
                case Some(login) =>
                    OptionT(AccountRepository.getById(login.accountId))
                        .map { acc => (acc, login) }
                        .getOrRaise( new Exception("not found") )
                case None => createWithAccount(ucampusUsername, mufasaId, name)
            }
        } yield accLogin

    private def createWithAccount(ucampusUsername: String, mufasaId: String, name: String): ConnectionIO[(Account, UcampusLogin)] =
        for {
            acc <- AccountRepository.create(name, mufasaId.some, AccountRole.NONE)
            login <- create(ucampusUsername, acc.id)
        } yield (acc, login)
}
