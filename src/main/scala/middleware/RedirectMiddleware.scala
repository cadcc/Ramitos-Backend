package cl.cadcc.ramitos.middleware

import cats.syntax.all.*
import cats.effect.syntax.all.*
import smithy4s.http4s.ServerEndpointMiddleware
import cats.MonadThrow
import smithy4s.Hints
import cats.data.Kleisli
import org.http4s.Request
import org.http4s.Response
import org.http4s.HttpApp
import cl.cadcc.ramitos.schema.Redirect
import org.http4s.Status
import cats.Functor

object RedirectMiddleware {

  given of[F[_]: Functor]: ServerEndpointMiddleware[F] = RedirectServerEndpointMiddleware[F]

  private class RedirectServerEndpointMiddleware[F[_]: Functor] extends ServerEndpointMiddleware.Simple[F] {

    override def prepareWithHints(serviceHints: Hints, endpointHints: Hints): HttpApp[F] => HttpApp[F] =
      endpointHints.get[Redirect] match {
        case Some(Redirect(code)) => httpTransform(Status.fromInt(code).getOrElse(throw AssertionError()))
        case None => identity
      }

    private def httpTransform(code: Status)(app: HttpApp[F]): HttpApp[F] =
      app.map(_.withStatus(code))
  }
}
