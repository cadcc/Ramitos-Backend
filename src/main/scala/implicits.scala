package cl.cadcc.ramitos

import cats.effect.IO
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import doobie.util.transactor.Transactor

object implicits {
    given logging: LoggerFactory[IO] = Slf4jFactory.create[IO]
}
