package cl.cadcc.ramitos.middleware

import org.typelevel.ci.given
import smithy4s.http4s.ClientEndpointMiddleware
import org.http4s.client.Client
import smithy4s.Hints
import cl.cadcc.ramitos.schema.AuthenticationService
import org.http4s.headers.Authorization
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.headers.given
import cats.effect.MonadCancelThrow
import smithy.api.HttpBearerAuth
import cl.cadcc.ramitos.schema.AuthenticationServiceOperation

object ClientAuth {

  def bearer[F[_]: MonadCancelThrow](token: String): ClientEndpointMiddleware[F] =
    AddAuth[F](token)

  private class AddAuth[F[_]: MonadCancelThrow as F](token: String) extends ClientEndpointMiddleware.Simple[F] {

    private def addToken(cl: Client[F]): Client[F] =
      Client[F] { req =>
        val newReq = req.putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token)))

        cl.run(newReq)
      }

    def prepareWithHints(serviceHints: Hints, endpointHints: Hints): Client[F] => Client[F] =
      serviceHints.get[HttpBearerAuth] match {
        case Some(_) =>
          endpointHints.get[smithy.api.Auth] match {
            case Some(auths) if auths.value.isEmpty => identity
            case _ => addToken
          }
        case None => identity
      }
  }
}
