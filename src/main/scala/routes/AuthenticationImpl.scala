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
import cl.cadcc.ramitos.utils.PortalDcc
import cats.data.OptionT
import org.typelevel.log4cats.LoggerFactory
import cats.effect.std.MapRef
import cats.effect.Unique
import java.time.Instant
import cats.effect.Spawn
import cats.effect.std.UUIDGen
import java.util.UUID
import org.http4s.Uri
import cl.cadcc.ramitos.config.DccLoginConfig
import cl.cadcc.ramitos.schema.DccLoginStartOutput
import cl.cadcc.ramitos.schema.DccLoginCallbackOutput
import cl.cadcc.ramitos.config.HttpConfig
import cl.cadcc.ramitos.schema.CallbackRejected
import cl.cadcc.ramitos.routes.Authentication.DccLoginState.WaitingCallback
import cats.data.EitherT
import cl.cadcc.ramitos.schema.StatisticallyImpossible
import org.http4s.RequestCookie
import org.http4s.headers.`Set-Cookie`
import org.http4s.headers.Cookie
import cl.cadcc.ramitos.schema.WorkflowTrackerCookieMissing
import cl.cadcc.ramitos.routes.Authentication.DccLoginState.WaitingTokenExchange
import cats.effect.std.Random
import java.util.Base64
import java.nio.charset.StandardCharsets
import cl.cadcc.ramitos.schema.WorkflowTimeout
import cl.cadcc.ramitos.schema.RequestReplayed
import cats.effect.Ref
import cl.cadcc.ramitos.model.Account
import cl.cadcc.ramitos.utils.PortalDcc.JwtValidationError
import cl.cadcc.ramitos.utils.PortalDcc.CallbackFormatError
import cl.cadcc.ramitos.utils.PortalDcc.NonceError
import cl.cadcc.ramitos.utils.PortalDcc.InvariantBroken
import cats.effect.Sync
import cats.effect.Concurrent
import cats.effect.Async

object Authentication {
    sealed abstract class DccLoginState {
        val expiresAt: Instant
    }
    object DccLoginState {
        final case class WaitingCallback(
            redirect: Uri,
            expiresAt: Instant
        ) extends DccLoginState

        final case class WaitingTokenExchange(
            account: Account,
            secret: String,
            expiresAt: Instant,
        ) extends DccLoginState
    }

    def ofAsync[F[_] : {
        Async,
        Transactor,
        PortalDcc,
        LoggerFactory,
        UUIDGen,
        Random,
    }](using
        JwtTokens[F, Session],
        Crypto,
        HttpConfig,
        DccLoginConfig
    ): F[AuthenticationImpl[F]] =
        MapRef[F, String, DccLoginState].map(sto => AuthenticationImpl[F](sto))
        

    class AuthenticationImpl[F[_] : {
        Transactor as xa,
        Clock as clk,
        MonadCancelThrow as F,
        PortalDcc as portal,
        LoggerFactory as logging,
        UUIDGen as uuidGen,
        Random as rand,
    }](sto: MapRef[F, String, Option[DccLoginState]])(
        using jwt: JwtTokens[F, Session],
        crypto: Crypto,
        httpConfig: HttpConfig,
        dccLoginConfig: DccLoginConfig,
    ) extends AuthenticationService[F] {

        private val logger = logging.getLogger

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

        private def validateRedirect(redirect: Option[Uri]): Either[CallbackRejected, Uri] =
            redirect match
              case None => httpConfig.baseUri.pure
              case Some(uri) =>
                for {
                    _ <- uri.scheme.traverse { scheme =>
                        Either.raiseUnless(scheme == httpConfig.scheme)(CallbackRejected()) }
                    _ <- Either.raiseUnless(uri.authority == httpConfig.authority)(CallbackRejected())
                } yield uri.copy(
                        scheme = httpConfig.scheme.some,
                        authority = httpConfig.authority.some
                    )

        private val cookieName = "WorkflowDccLogin"

        override def dccLoginStart(redirect: Option[String]): F[DccLoginStartOutput] =
            for {
                now <- clk.realTimeInstant
                uuid <- uuidGen.randomUUID.map(_.toString)
                redirectUri <- redirect.traverse { str =>
                    EitherT.fromEither(Uri.fromString(str)).rethrowT
                }
                finalRedirect <- EitherT.fromEither(validateRedirect(redirectUri)).rethrowT
                state = WaitingCallback(
                    redirect = finalRedirect,
                    expiresAt = now.plusSeconds(dccLoginConfig.loginTimeLimitSeconds),
                )
                _ <- sto(uuid).modify {
                    case None => (state.some, F.unit)
                    case oldOpt @ Some(old) =>
                        if old.expiresAt.isBefore(now) then (state.some, F.unit)
                        else (
                            oldOpt,
                            F.raiseError(
                                StatisticallyImpossible("Wow, you just found 2 UUIDs colliding! go buy a lottery ticket")))
                }.flatten
                portalUri <- portal.authUri
                maxAge = dccLoginConfig.loginTimeLimitSeconds * 3
                cookie = s"${cookieName}=\"${uuid}\"; Path=/api/workflow/login/dcc; HttpOnly; Max-Age=${maxAge}"
            } yield DccLoginStartOutput(portalUri.toString, cookie)

        private val encoder = Base64.getEncoder
        private val startUri = httpConfig.baseUri / "api" / "workflow" / "login" / "dcc" / "start"

        private def getWorkflowState(cookies: String): F[Ref[F, Option[DccLoginState]]] =
            for {
                parsedCookies <- EitherT.fromEither(Cookie.parse(cookies)).rethrowT
                workflowCookieOpt =
                    parsedCookies.values
                        .find { _.name == cookieName }
                workflowCookie <-
                    OptionT.fromOption(workflowCookieOpt)
                        .getOrRaise(WorkflowTrackerCookieMissing(cookieName))
                uuid = workflowCookie.content
            } yield sto(uuid)
        
        override def dccLoginCallback(params: Map[String, String], cookies: String): F[DccLoginCallbackOutput] =
            for {
                now <- clk.realTimeInstant
                stoState <- getWorkflowState(cookies)
                secret <- rand.nextBytes(24)
                    .map(encoder.encode)
                    .map(arr => String(arr, StandardCharsets.UTF_8))
                (acc, login) <- EitherT(portal.validate(params))
                // TODO: debug messages
                    .leftMap(err => CallbackRejected())
                    .rethrowT

                state = WaitingTokenExchange(
                    acc,
                    secret,
                    now.plusSeconds(dccLoginConfig.loginTimeLimitSeconds))
                redirectUri <- stoState.modify {
                    case oldOpt @ None => (
                        oldOpt,
                        F.raiseError[Uri](WorkflowTimeout(startUri.toString)))
                    case oldOpt @ Some(old : WaitingTokenExchange) => (
                        oldOpt,
                        F.raiseError[Uri](RequestReplayed()))
                    case oldOpt @ Some(WaitingCallback(redirect, expiresAt)) =>
                        if expiresAt.isBefore(now) then (None, F.raiseError[Uri](WorkflowTimeout(startUri.toString)))
                        else (state.some, F.pure(redirect))
                }.flatten
                finalUri = redirectUri +? ("secret", secret) +? ("status", "ok") +? ("workflow", "dccLogin")
            } yield DccLoginCallbackOutput(finalUri.toString)

        override def dccLoginExchangeTokens(secret: String, cookies: String): F[SessionTokens] =
            for {
                now <- clk.realTimeInstant
                stoState <- getWorkflowState(cookies)
                (acc, expectedSecret) <-
                    stoState.modify {
                         case None => (
                             None,
                             F.raiseError(WorkflowTimeout(startUri.toString)))
                         case old @ Some(WaitingCallback(_, _)) => (
                             old,
                             F.raiseError(WorkflowTimeout(startUri.toString))
                         )
                         case old @ Some(WaitingTokenExchange(acc, expectedSecret, expirestAt)) =>
                             if expirestAt.isBefore(now) then (None, F.raiseError(WorkflowTimeout(startUri.toString)))
                             else (None, (acc, expectedSecret).pure)
                    }.flatten
                session = Session(acc, LoginMethod.UCampus)
                accessToken <- JwtTokens[F, Session].makeAccessToken(session)
            } yield SessionTokens(accessToken)
    }
}
