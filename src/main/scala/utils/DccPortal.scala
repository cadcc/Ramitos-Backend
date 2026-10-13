package cl.cadcc.ramitos.utils

import cats.*
import cats.effect.Concurrent
import cats.syntax.all.*
import doobie.syntax.all.*
import io.circe.{Codec, Decoder}
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.Client
import org.typelevel.log4cats.{Logger, LoggerFactory}
import pureconfig.ConfigReader.Result
import cl.cadcc.ramitos.utils.PortalDcc.ValidationError
import cl.cadcc.ramitos.config.PortalDccConfig
import cats.effect.MonadCancelThrow
import cl.cadcc.ramitos.JwtTokens
import cats.data.OptionT
import cats.effect.Clock
import cats.data.EitherT
import cl.cadcc.ramitos.model.DccLogin
import cl.cadcc.ramitos.schema.AccountService
import cl.cadcc.ramitos.repository.DccLoginRepository
import cl.cadcc.ramitos.model.Account
import doobie.util.transactor.Transactor
import cl.cadcc.ramitos.JwtJsonException
import cl.cadcc.ramitos.JwtValidationException
import cats.effect.std.MapRef
import cats.effect.std.SecureRandom
import java.util.Base64
import java.nio.charset.StandardCharsets
import cats.effect.kernel.Sync
import java.time.Instant

trait PortalDcc[F[_]] {
    def authUri: F[Uri]
    def validate(queryParams: Map[String, String]): F[Either[ValidationError, (Account, DccLogin)]]
}

object PortalDcc {

    def apply[F[_]](using ev: PortalDcc[F]): PortalDcc[F] = ev

    def ofConf[
        F[_]: {
            Sync as F,
            Concurrent,
            Transactor,
            LoggerFactory,
            SecureRandom},
    ](config: PortalDccConfig)
    : F[PortalDcc[F]] =
        given MonadCancelThrow[F] = F
        for {
            jwt = JwtTokens.ofClock[F, CallbackData](config.jwt)
            nonces <- MapRef[F, String, Timeout]
        } yield PortalDccImpl[F](config, jwt, nonces)

    sealed abstract class ValidationError(val message: String, val cause: Throwable) extends Exception(message, cause)
    case class JwtValidationError(override val cause: Throwable) extends ValidationError("Failed to validate JWT signature", cause)
    case class CallbackFormatError(override val message: String) extends ValidationError(message, null)
    case class NonceError(override val message: String) extends ValidationError(message, null)
    case class InvariantBroken(override val message: String) extends ValidationError(message, null)
    case class TimetoutError(override val message: String) extends ValidationError(message, null)

    private case class CallbackData(
        name: String,
        given_name: String,
        family_name: String,
        social_name: String,
        preferred_username: String,
        email: String,
        picture: String,
        identification: String,
        nonce: String,
    ) derives Codec

    private type Timeout = Instant
    private class PortalDccImpl[
        F[_]: {
            MonadCancelThrow as F,
            Transactor as xa,
            SecureRandom as rand,
            Clock as clk
        }
    ](
        config: PortalDccConfig,
        jwtTokens: JwtTokens[F, CallbackData],
        nonces: MapRef[F, String, Option[Timeout]],
    ) extends PortalDcc[F] {
        private val encoder = Base64.getEncoder
        
        override val authUri: F[Uri] =
            for {
                now <- clk.realTimeInstant
                nonce <- rand.nextBytes(24)
                    .map(encoder.encode)
                    .map(arr => String(arr, StandardCharsets.UTF_8))
                _ <- nonces(nonce).flatModify {
                    case None => (Some(now.plusSeconds(config.jwt.accessTokenLifeSeconds + config.jwt.leewaySeconds)), F.unit)
                    case Some(_) => (None, NonceError("Statistically imposible event just happend... try again!").raiseError)
                }
                url = config.baseUrl +? ("app", config.appId) +? ("nonce", nonce)
            } yield url

        override def validate(queryParams: Map[String, String]): F[Either[ValidationError, (Account, DccLogin)]] =
            (for {
                jwt <- EitherT
                    .fromOption(
                        queryParams.get("jwt"),
                        CallbackFormatError("Missing 'jwt' query parameter."))
                data <- EitherT(jwtTokens.verifyAccessToken(jwt))
                    .leftMap[ValidationError](JwtValidationError.apply)
                nonce = data.nonce
                now <- EitherT.right(clk.realTimeInstant)
                _ <- EitherT(nonces(nonce).modify {
                    case Some(timeout) if now.isBefore(timeout) => (None, ().asRight)
                    case Some(_) => (None, TimetoutError("Login attempt expired. Try again").asLeft)
                    case x@None => (x, NonceError("nonce not recognized. You are being naughty, aren't you?").asLeft)
                })
                mufasaId <- EitherT.fromEither(obtainMufasaId(data.identification))
                // TODO: verify mufasaId?
                ans <- EitherT(
                    DccLoginRepository.getOrCreateAccount(
                        ucampusUsername = mufasaId,
                        mufasaId = mufasaId,
                        name = computeDisplayName(data))
                    .transact(xa)
                    .map(_.asRight[ValidationError]))
            } yield ans).value

        private def computeDisplayName(data: CallbackData): String =
            val root =
                if data.social_name.length >= 1 then
                    data.social_name
                else data.given_name
            val fstSpace = root.indexWhere(_ == ' ') match {
                case -1 => root.length
                case n => n
            }
            root.substring(0, fstSpace)
        
        private def obtainMufasaId(id: String): Either[ValidationError, String] =
            for {
                idStrip = id.strip()
                len = idStrip.length
                _ <- Either.cond(
                    9 <= len && len <= 15 && idStrip.forall(('0' to '9').contains),
                    (),
                    InvariantBroken("Expected identification to be rut-like"))
                // mufasaId with leading zeros
                firstNotZero <- idStrip.indexWhere(_ != '0') match {
                    case -1 => InvariantBroken("Expected identification to be rut-like").asLeft
                    case n => n.asRight
                }
            } yield idStrip.substring(firstNotZero, len-1)
    }
}
