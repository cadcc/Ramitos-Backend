package cl.cadcc.ramitos.routes

import cats.syntax.all.*
import cl.cadcc.ramitos.model.{AccountRole, Stat, given}
import cats.MonadThrow
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cats.mtl.Ask
import cl.cadcc.ramitos.schema.ReviewStats

private[routes] object utils {
    def minPermission[F[_]](role: AccountRole)(using MonadThrow[F])(using ask: Ask[F, Session]): F[Unit] =
        ask.ask.flatMap( session =>
            MonadThrow[F].raiseUnless(role <= session.account.role)(new Exception("Insufficient Permissions"))
        )

    def statsToSchema(m: Map[Stat, Option[Byte]]): ReviewStats =
        ReviewStats(
            docencia = m(Stat.DOCENCIA),
            vibes = m(Stat.VIBES),
            relevancia = m(Stat.RELEVANCIA),
            carga = m(Stat.CARGA),
            dificultad = m(Stat.DIFICULTAD),
        )
}
