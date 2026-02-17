package cl.cadcc.ramitos.middleware

import cl.cadcc.ramitos.utils.extensions._
import cats.syntax.all._
import cats.effect.IO
import cats.effect.IOLocal
import cl.cadcc.ramitos.utils._
import cats.mtl.Local
import smithy4s.http4s.ServerEndpointMiddleware
import smithy4s.Hints
import cats.data.Kleisli
import org.http4s.Request
import org.http4s.Response
import org.http4s.HttpApp
import cats.mtl.Ask
import smithy.api.HttpBearerAuth
import smithy.api.Auth
import cats.MonadThrow
import cats.effect.Clock
import org.http4s.headers.Authorization
import org.http4s.Credentials
import org.http4s.AuthScheme
import cl.cadcc.ramitos.JwtTokens
import cl.cadcc.ramitos.model.Account
import io.circe.Codec
import cl.cadcc.ramitos.schema.NotAuthenticated
import scala.util.Failure
import scala.util.Success
import cl.cadcc.ramitos.JwtJsonException

trait AuthMiddleware[F[_], E] {
    val askSession: Ask[F, E]

    val middleware: ServerEndpointMiddleware[F]
}

object AuthMiddleware {

    enum LoginMethod derives Codec:
        case Password
        case UCampus

    case class Session(account: Account, method: LoginMethod) derives Codec

    type T = Session

    type AskSession = Ask[IO, T]

    def apply: IO[AuthMiddleware[IO, T]] =
        for {
            local <- IOLocal(None : Option[T])
        } yield AuthMiddlewareImpl[IO](local.asLocal)

    private class AuthMiddlewareImpl[F[_]](using MonadThrow[F], Clock[F])(private val localSession: Local[F, Option[T]]) extends AuthMiddleware[F, T] {
        val askSession: Ask[F, T] = localSession.toGetSome("Asked for a session outside of the context of an authenticated endpoint.")

        val middleware = AuthServerEndpointMiddlewareImpl(localSession)
    }

    private class AuthServerEndpointMiddlewareImpl[F[_]](using MonadThrow[F], Clock[F])(val localSession: Local[F, Option[T]]) extends ServerEndpointMiddleware.Simple[F] {
        def prepareWithHints(serviceHints: Hints, endpointHints: Hints): HttpApp[F] => HttpApp[F] =
            serviceHints.get[HttpBearerAuth] match {
                case Some(value) =>
                    endpointHints.get[Auth] match {
                        case Some(value) if value.value.isEmpty => identity
                        case _ => httpTransform 
                    }
                case None => identity
            }

        private def tryGetToken[F[_]](req: Request[F]) =
            req.headers
                .get[Authorization]
                .collect {
                    case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => Success(token)
                    case Authorization(Credentials.Token(_, _)) =>
                        Failure(new NotAuthenticated(
                            reason = "Invalid Authentication scheme.".some,
                            message = "Retry using Bearer scheme.".some
                        ))
                }
                .getOrElse(Failure(new NotAuthenticated(
                    reason = "Missing Authentication header.".some,
                    message = "Retry including a valid Bearer token.".some
                )))
        
        private def httpTransform(app: HttpApp[F]): HttpApp[F] = Kleisli {(req: Request[F]) =>
            for {
                token <-
                    req.headers
                        .get[Authorization]
                        .collect {
                            case Authorization(Credentials.Token(AuthScheme.Bearer, token)) => Success(token)
                            case Authorization(_) =>
                                Failure(new NotAuthenticated(
                                    reason = "Invalid Authentication scheme.".some,
                                    message = "Retry using Bearer scheme.".some
                                ))
                        }
                        .getOrElse(Failure(new NotAuthenticated(
                            reason = "Missing Authentication header.".some,
                            message = "Retry including a valid Bearer token.".some
                        )))
                        .getM
                session <-
                    JwtTokens[F, T].verifyAccessToken(token)
                        .flatMap(_.getM)
                        .adaptErr {
                            case JwtJsonException(reason, cause) => new NotAuthenticated(
                                reason = reason.some,
                                message = cause.getClass.getCanonicalName.some
                            )
                        }
                response <- localSession.scope(app(req))(session.some)
            } yield response
        }
    }
}
