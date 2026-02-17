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

    given fromClock[F[_]](using clock: Clock[F]): JavaTime[F] =
        TimeImpl[F](using clock)

    private class TimeImpl[F[_]](using clock: Clock[F]) extends JavaTime[F] {
        given Applicative[F] = clock.applicative

        val getInstant: F[Instant] = clock.realTimeInstant

        val getLocalDateTimeUtc: F[LocalDateTime] =
            clock.realTimeInstant.map(_.atZone(ZoneOffset.UTC).toLocalDateTime())

        val getEpochSeconds: F[Long] =
            clock.realTime.map(_.toSeconds)
    }
}
