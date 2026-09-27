package cl.cadcc.ramitos.repository

import cats.*
import cats.syntax.all.*
import cats.implicits.given
import cats.data.{NonEmptyVector, OptionT}
import cl.cadcc.ramitos.model.{Account, AccountRole, DccLogin}
import doobie.free.connection.ConnectionIO
import doobie.syntax.all.*
import doobie.implicits.given

import scala.language.implicitConversions

object DccLoginRepository {
    val Table = DccLogin.Table

    def getDccLogin(ucampusUsername: String): ConnectionIO[Option[DccLogin]] =
        sql"SELECT ${Table.columns} FROM $Table WHERE ${Table.ucampusId === ucampusUsername}"
            .query[DccLogin]
            .option

    def create(ucampusUsername: String, accountId: Int): ConnectionIO[DccLogin] =
        Table.insertInto(NonEmptyVector.of(
            Table.ucampusId --> ucampusUsername,
            Table.accountId --> accountId
        )).update
            .withUniqueGeneratedKeys("ucampus_id", "account_id", "created_at")

    def getOrCreateAccount(ucampusUsername: String, mufasaId: String, name: String): ConnectionIO[(Account, DccLogin)] =
        for {
            ucampusLogin <- getDccLogin(ucampusUsername)
            accLogin <- ucampusLogin match {
                case Some(login) =>
                    OptionT(AccountRepository.getById(login.accountId))
                        .map { acc => (acc, login) }
                        .getOrRaise( new Exception("not found") )
                case None => createWithAccount(ucampusUsername, mufasaId, name)
            }
        } yield accLogin

    private def createWithAccount(ucampusUsername: String, mufasaId: String, name: String): ConnectionIO[(Account, DccLogin)] =
        for {
            acc <- AccountRepository.create(name, mufasaId.some, AccountRole.NONE)
            login <- create(ucampusUsername, acc.id)
        } yield (acc, login)
}
