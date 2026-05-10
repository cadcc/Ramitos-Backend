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


class AccountImpl[F[_]](session: Ask[F, Session])(using crypto: Crypto)(using MonadCancelThrow[F], Clock[F], Transactor[F]) extends AccountService[F] {
    private given Ask[F, Session] = session

    def getSelf(): F[SAccount] =
        session.ask.map(s => schemaConvert[Account, SAccount](s.account))

    def createAccount(username: String, password: String, role: SchemaRole, name: String): F[SAccount] =
        minPermission(AccountRole.ADMIN) *>
        (for {
            acc <- AccountRepository.create(name, role.toModel())
            hash = crypto.hashPassword(password)
            pass <- PasswordRepository.create(username, hash, acc.id)
        } yield acc)
            .transact(xa)
            .map(acc => schemaConvert[Account, SAccount](acc))

    def updateAccount(userId: String, name: Option[String]): F[SAccount] = ???
}
