package cl.cadcc.ramitos.routes

import doobie._, cats.syntax.all._, cats.effect.syntax.all._
import doobie.implicits._, cats.implicits._, cats.effect.implicits._


import cats.effect.kernel.Clock
import cl.cadcc.ramitos.schema.AuthenticationService
import cl.cadcc.ramitos.schema.SessionTokens
import cats.MonadThrow
import cl.cadcc.ramitos.utils.Crypto
import doobie.util.transactor.Transactor
import doobie.ConnectionIO
import cl.cadcc.ramitos.model.Password
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.MonadCancelThrow
import cl.cadcc.ramitos.repository.AccountRepository
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cl.cadcc.ramitos.middleware.AuthMiddleware.LoginMethod
import cl.cadcc.ramitos.JwtTokens
import cl.cadcc.ramitos.repository.PasswordRepository
import cl.cadcc.ramitos.repository.UcampusLoginRepository
import cl.cadcc.ramitos.utils.DccPortal
import cats.data.OptionT
import org.typelevel.log4cats.LoggerFactory

object Authentication {
    class AuthenticationImpl[F[_]](using xa: Transactor[F], crypto: Crypto)(using Clock[F], MonadCancelThrow[F], JwtTokens[F, Session], DccPortal[F], LoggerFactory[F]) extends AuthenticationService[F] {
        private val logger = LoggerFactory[F].getLogger

        def passwordLogin(username: String, password: String): F[SessionTokens] =
            for {
                credsOpt <- PasswordRepository.getByUsername(username).transact(xa)
                creds <- credsOpt match
                    case Some(value) => value.pure[F]
                    case None => MonadThrow[F].raiseError(new Exception("Invalid credentials."))
                _ <- MonadThrow[F].raiseUnless(crypto.verifyPassword(password, creds.secret))(new Exception("Invalid credentials"))
                accOpt <- AccountRepository.getById(creds.accountId).transact(xa)
                acc <- accOpt match
                    case Some(value) => value.pure[F]
                    case None => MonadThrow[F].raiseError(new RuntimeException("Credentials valid, but no account found."))
                session = Session(acc, LoginMethod.Password)
                accessToken <- JwtTokens[F, Session].makeAccessToken(session)
            } yield SessionTokens(accessToken)
        
        def dccLogin(username: String, secret: String): F[SessionTokens] =
            for {
                ucampusUser <- OptionT(DccPortal[F].getUser(username,secret))
                    .getOrRaise(RuntimeException("DccPortal login was not valid"))
                (acc, login) <- UcampusLoginRepository.getOrCreateAccount(ucampusUser.username, ucampusUser.first_name)
                        .transact(xa)
                session = Session(acc, LoginMethod.UCampus)
                accessToken <- JwtTokens[F, Session].makeAccessToken(session)
            } yield SessionTokens(accessToken)
    }
}
