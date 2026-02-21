package cl.cadcc.ramitos.routes

import cats.syntax.all._
import cl.cadcc.ramitos.model.{AccountRole, given}
import cats.MonadThrow
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cats.mtl.Ask

private[routes] object utils {
    def minPermission[F[_]](role: AccountRole)(using MonadThrow[F])(using ask: Ask[F, Session]): F[Unit] =
        ask.ask.flatMap( session =>
            MonadThrow[F].raiseUnless(role <= session.account.role)(new Exception("Insufficient Permissions"))
        )
}
