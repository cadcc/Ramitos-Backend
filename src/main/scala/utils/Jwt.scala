package cl.cadcc.ramitos

import cl.cadcc.ramitos.config.JwtConfig
import cl.cadcc.ramitos.model.Account
import cl.cadcc.ramitos.utils.JavaTime
import cats._
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.effect.IO
import io.circe.syntax._
import pdi.jwt.JwtClaim
import java.time.Instant
import cats.effect.kernel.Clock
import io.circe.parser._
import io.circe.Codec
import pdi.jwt.JwtAlgorithm
import scala.concurrent.duration._
import pdi.jwt.{JwtCirce, Jwt}
import pdi.jwt.exceptions.{JwtException => LibJwtException}
import java.time.{Clock => JavaClock, ZoneId}
import scala.util.Success
import scala.util.Try
import scala.util.Failure
import pdi.jwt.JwtOptions
import javax.security.auth.login.CredentialExpiredException
import javax.security.auth.login.CredentialException
import cats.data.EitherT
import cats.effect.MonadCancelThrow
import org.typelevel.log4cats.LoggerFactory

trait JwtTokens[F[_], Payload] {
    def verifyAccessToken(token: String): F[Either[JwtException, Payload]]
    def makeAccessToken(data: Payload): F[String]
}

sealed abstract class JwtException(reason: String, cause: Throwable) extends Exception(reason, cause)
case class JwtJsonException(reason: String, cause: Throwable) extends JwtException(reason, cause)
case class JwtValidationException(reason: String) extends JwtException(reason, null)

object JwtTokens {

    def apply[F[_], E](using ev: JwtTokens[F, E]) = ev

    def ofClock[F[_]: {MonadCancelThrow, LoggerFactory, Clock}, E: Codec](conf: JwtConfig): JwtTokens[F, E] = JwtTokensImpl(conf)

    private class JwtTokensImpl[
        F[_] : {MonadCancelThrow as F,
                LoggerFactory as logging,
                Clock as clk},
        E    : {Codec as codec}
    ](private val conf: JwtConfig) extends JwtTokens[F, E] {
        private val logger = logging.getLogger

        private val secretKey = conf.secretKey
        private val algo = JwtAlgorithm.HS256
        private val accessExpirity = conf.accessTokenLifeSeconds.seconds
        private val leeway = conf.leewaySeconds.seconds
        
        private val jwtOptions: JwtOptions = JwtOptions(
            signature = true,
            expiration = false,
            notBefore = false,
            leeway = 0
        )

        def verifyAccessToken(token: String): F[Either[JwtException, E]] =
            (for {
                now <- EitherT(clk.realTimeInstant.map(_.getEpochSecond().asRight[JwtException]))
                claimsF =
                    JwtCirce.decode(token, secretKey, Seq(algo), jwtOptions) match
                      case Failure(e : LibJwtException) => JwtJsonException("Failed to parse JWT claims", e).asLeft.pure
                      case Failure(e) => e.raiseError
                      case Success(value) => value.asRight.pure
                claims <- EitherT(claimsF)
                _ <- EitherT.fromEither(verifyTokenTime(claims, now))
                session <- EitherT.fromEither(
                    decode[E](claims.content)
                        .leftMap[JwtException](err => JwtJsonException("The JWT content was not deserializable into a Session instance.", err)))
            } yield session).value

        def makeAccessToken(data: E): F[String] =
            JavaTime[F].getInstant.map(now =>
                val claims =
                    JwtClaim(
                        content = data.asJson.show,
                        issuer = "cl.cadcc.ramitos".some,
                        issuedAt = now.getEpochSecond().some,
                        expiration = now.plusSeconds(accessExpirity.toSeconds).getEpochSecond().some,
                    )
                JwtCirce.encode(claims, secretKey, algo)
            )
        
        private def verifyTokenTime(claims: JwtClaim, epochSeconds: Long): Either[JwtException, Unit] =
            for {
                _ <- claims.expiration match
                        case Some(value) if epochSeconds <= value + leeway.toSeconds => ().asRight
                        case None => ().asRight
                        case _ => JwtValidationException("The token has expired.").asLeft
                _ <- claims.notBefore match
                        case Some(value) if value <= epochSeconds + leeway.toSeconds => ().asRight
                        case None => ().asRight
                        case _ => JwtValidationException("The token was provided before is valid.").asLeft
            } yield ()
    }
}
