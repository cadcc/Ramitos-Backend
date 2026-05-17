package cl.cadcc.ramitos.utils

import java.time.Instant
import cats.syntax.all._
import cats.effect.kernel.Clock
import cats.effect.syntax.all._
import cats.Show
import scala.math.Integral.Implicits.infixIntegralOps
import java.time.LocalDateTime
import cats.Applicative
import java.time.ZoneOffset

trait JavaTime[F[_]] {
    val getInstant: F[Instant]
    val getLocalDateTimeUtc: F[LocalDateTime]
    val getEpochSeconds: F[Long]
}

object JavaTime {

    def apply[F[_]](using ev: JavaTime[F]): JavaTime[F] = ev

    given fromClock[F[_]](using Clock[F]): JavaTime[F] =
        TimeImpl[F]

    private class TimeImpl[F[_]: Clock as F] extends JavaTime[F] {
        given Applicative[F] = F.applicative

        val getInstant: F[Instant] = F.realTimeInstant

        val getLocalDateTimeUtc: F[LocalDateTime] =
            F.realTimeInstant.map(_.atZone(ZoneOffset.UTC).toLocalDateTime())

        val getEpochSeconds: F[Long] =
            getInstant.map(_.getEpochSecond)
    }
}
