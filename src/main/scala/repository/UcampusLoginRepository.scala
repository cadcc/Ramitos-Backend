package cl.cadcc.ramitos.repository

import cats.*
import cats.syntax.all.*
import cats.implicits.given
import cats.data.NonEmptyVector
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

    def getOrCreateAccount(ucampusUsername: String, name: String): ConnectionIO[(Option[Account], UcampusLogin)] =
        for {
            ucampusLogin <- getUcampusLogin(ucampusUsername)
            accLogin <- ucampusLogin match {
                case Some(value) => (None, value).pure[ConnectionIO]
                case None => createWithAccount(ucampusUsername, name).map { (a, l) => (a.some, l) }
            }
        } yield accLogin

    private def createWithAccount(ucampusUsername: String, name: String): ConnectionIO[(Account, UcampusLogin)] =
        for {
            acc <- AccountRepository.create(name, AccountRole.none)
            login <- create(ucampusUsername, acc.id)
        } yield (acc, login)
}
