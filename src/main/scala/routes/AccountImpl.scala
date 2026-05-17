package cl.cadcc.ramitos.routes

import cats._, cats.syntax.all._, cats.effect.syntax.all._, doobie.syntax.all._
import cats.mtl.Ask
import cl.cadcc.ramitos.schema.{AccountService, AccountRole => SchemaRole}
import cl.cadcc.ramitos.model.{Account, given}
import cl.cadcc.ramitos.schema.{Account => SAccount}
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cl.cadcc.ramitos.utils.Shapeless.{schemaConvert, given}
import cl.cadcc.ramitos.repository.AccountRepository
import cats.effect.Clock
import cl.cadcc.ramitos.utils.extensions.toModel
import cl.cadcc.ramitos.model.Password
import cl.cadcc.ramitos.utils.Crypto
import cl.cadcc.ramitos.RamitosContext.xa
import doobie.util.transactor.Transactor
import cats.effect.MonadCancelThrow
import cl.cadcc.ramitos.routes.utils.minPermission
import cl.cadcc.ramitos.model.AccountRole
import cl.cadcc.ramitos.repository.PasswordRepository


class AccountImpl[F[_]: {MonadCancelThrow as F, Clock, Transactor}](using askSession: Ask[F, Session], crypto: Crypto) extends AccountService[F] {
    def getSelf(): F[SAccount] =
        askSession.ask.map(s => schemaConvert[Account, SAccount](s.account))

    def createAccount(username: String, password: String, role: SchemaRole, name: String): F[SAccount] =
        minPermission(AccountRole.ADMIN) *>
        (for {
            acc <- AccountRepository.create(name, role.toModel)
            hash = crypto.hashPassword(password)
            pass <- PasswordRepository.create(username, hash, acc.id)
        } yield acc)
            .transact(xa)
            .map(acc => schemaConvert[Account, SAccount](acc))

    def updateAccount(userId: String, name: Option[String]): F[SAccount] = ???
}
