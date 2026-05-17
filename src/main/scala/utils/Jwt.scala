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

trait JwtTokens[F[_], Payload] {
    def verifyAccessToken(token: String): F[Try[Payload]]
    def makeAccessToken(data: Payload): F[String]
}

sealed trait JwtException(reason: String, cause: Option[Throwable]) extends Exception
case class JwtJsonException(reason: String, cause: Throwable) extends JwtException(reason, cause.some)
case class JwtValidationException(reason: String) extends JwtException(reason, None)

object JwtTokens {

    def apply[F[_], E](using ev: JwtTokens[F, E]) = ev

    def ofClock[F[_], E](conf: JwtConfig)(using Clock[F], Codec[E]): JwtTokens[F, E] = JwtTokensImpl(conf)

    private class JwtTokensImpl[F[_], E](private val conf: JwtConfig)(using clk: Clock[F], codec: Codec[E]) extends JwtTokens[F, E] {
        given app: Applicative[F] = clk.applicative

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

        def verifyAccessToken(token: String): F[Try[E]] =
            JavaTime[F].getEpochSeconds.map(now =>
                for {
                    claims <- JwtCirce.decode(token, secretKey, Seq(algo), jwtOptions).adaptErr {
                        case e : LibJwtException => JwtValidationException(e.getMessage())
                    }
                    _ <- verifyTokenTime(claims, now)
                    session <- decode[E](claims.content) match
                        case Left(value) => Failure(JwtJsonException("The JWT content was not deserializable into a Session instance.", value))
                        case Right(value) => Success(value)
                } yield session
            )

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
        
        private def verifyTokenTime(claims: JwtClaim, epochSeconds: Long): Try[Unit] =
            for {
                _ <- claims.expiration match
                        case Some(value) if epochSeconds <= value + leeway.toSeconds => Success(())
                        case None => Success(())
                        case _ => Failure(JwtValidationException("The token has expired."))
                _ <- claims.notBefore match
                        case Some(value) if value <= epochSeconds + leeway.toSeconds => Success(())
                        case None => Success(())
                        case _ => Failure(JwtValidationException("The token was provided before is valid."))
            } yield ()
    }
}
