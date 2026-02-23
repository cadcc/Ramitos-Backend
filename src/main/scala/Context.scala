package cl.cadcc.ramitos

import doobie.util.transactor.Transactor
import cats.effect.syntax.all._
import cats.effect.implicits._
import cats.effect.IO
import cats.implicits._
import cats.syntax.all._
import cats.effect.Resource
import cats.mtl.Ask
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cl.cadcc.ramitos.middleware.AuthMiddleware
import org.typelevel.log4cats.LoggerFactory
import org.http4s.client.Client

case class RamitosContext[F[_]](
    val xa: Transactor[F],
    val config: Unit,
    val auth: AuthMiddleware[F, Session],
    val logging: LoggerFactory[F],
    val httpClient: Client[F]
)

object RamitosContext {

    def apply[F[_]](using ev: RamitosContext[F]): RamitosContext[F] = ev
    def xa[F[_]](using ev: Transactor[F]): Transactor[F] = ev
    def logging[F[_]](using ev: RamitosContext[F]): LoggerFactory[F] = ev.logging
    def httpClient[F[_]](using ev: RamitosContext[F]): Client[F] = ev.httpClient

    def getTransactor: Resource[IO, Transactor[IO]] =
        Resource.eval(Transactor.fromDriverManager[IO](
            driver = "org.postgresql.Driver",
            url = "jdbc:postgresql://localhost:5432/ramitos",
            user = "postgres",
            password = "postgres",
            logHandler = None
        ).pure)
    
    given transactorFromContext[F[_]](using ctx: RamitosContext[F]): Transactor[F] = ctx.xa
    given clientFromContext[F[_]](using ctx: RamitosContext[F]): Client[F] = ctx.httpClient
}
