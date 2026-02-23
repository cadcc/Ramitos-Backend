package cl.cadcc.ramitos

import org.http4s._, org.http4s.dsl.io._, org.http4s.implicits._, org.http4s.circe._
import io.circe.generic.auto._, io.circe.syntax._
import cats.effect.{IO, IOApp}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import cl.cadcc.ramitos.routes.restRoutes
import cats.effect.ExitCode
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.{port, host}
import cats.effect.Resource
import cl.cadcc.ramitos.RamitosContext.getTransactor
import doobie.util.transactor.Transactor
import cl.cadcc.ramitos.middleware.AuthMiddleware
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cats.mtl.syntax.raise
import cats.MonadThrow
import cl.cadcc.ramitos.schema.NotAuthenticated
import org.http4s.headers.`WWW-Authenticate`
import cats.data.NonEmptyList
import org.http4s.ember.client.EmberClientBuilder

object Main extends IOApp {
    val logging = Slf4jFactory.create[IO]
    val mainLogger = logging.getLogger
    
    private val resources: Resource[IO, RamitosContext[IO]] =
        for {
            xa <- getTransactor
            auth <- AuthMiddleware.apply.toResource
            client <- EmberClientBuilder.default[IO].build
        } yield RamitosContext(xa, (), auth, logging, client)

    override def run(args: List[String]): IO[ExitCode] =
        resources.flatMap {rctx =>
            given ctx: RamitosContext[IO] = rctx

            for {
                routes <- restRoutes
                server <- EmberServerBuilder.default[IO]
                    .withPort(port"8000")
                    .withHost(host"localhost")
                    .withHttpApp(routes.orNotFound)
                    .withErrorHandler({
                        case e @ NotAuthenticated(reason, message) =>
                            Unauthorized(
                                `WWW-Authenticate`(NonEmptyList.one(Challenge("Bearer", "ramitos"))),
                                e.asJson)
                        case t =>
                            mainLogger.error(t)("Internal Server Error")
                            *> MonadThrow[IO].raiseError(t)
                    })
                    .build
            } yield server
        }.use { _ => IO.never }
        .as(ExitCode.Success)
}