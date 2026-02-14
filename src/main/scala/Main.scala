package cl.cadcc.ramitos

import cats.effect.{IO, IOApp}
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import cl.cadcc.ramitos.routes.restRoutes
import cats.effect.ExitCode
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.{port, host}

object Main extends IOApp:
  val logger = LoggerFactory[IO].getLogger

  override def run(args: List[String]): IO[ExitCode] =
    restRoutes.flatMap { routes =>
      EmberServerBuilder.default[IO]
        .withPort(port"9000")
        .withHost(host"localhost")
        .withHttpApp(routes.orNotFound)
        .build
    }.use { _ => IO.never }
    .as(ExitCode.Success)
