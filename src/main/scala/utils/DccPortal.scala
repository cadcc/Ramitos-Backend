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
import cl.cadcc.ramitos.model.UcampusLogin
import cl.cadcc.ramitos.schema.AccountService
import cl.cadcc.ramitos.repository.UcampusLoginRepository
import cl.cadcc.ramitos.model.Account
import doobie.util.transactor.Transactor
import cl.cadcc.ramitos.JwtJsonException
import cl.cadcc.ramitos.JwtValidationException

trait PortalDcc[F[_]] {
    def authUri: F[Uri]
    def validate(queryParams: Map[String, String]): F[Either[ValidationError, (Account, UcampusLogin)]]
}

object PortalDcc {

    def apply[F[_]](using ev: PortalDcc[F]): PortalDcc[F] = ev

    def ofConf[
        F[_]: {
            MonadCancelThrow,
            Transactor,
            LoggerFactory,
            Clock},
    ](config: PortalDccConfig)
    : PortalDcc[F] =
        given PortalDccConfig = config
        given JwtTokens[F, CallbackData] = JwtTokens.ofClock(config.jwt)
        PortalDccImpl[F]

    sealed abstract class ValidationError(val message: String, val cause: Throwable) extends Exception(message, cause)
    case class JwtValidationError(override val cause: Throwable) extends ValidationError("Failed to validate JWT signature", cause)
    case class CallbackFormatError(override val message: String) extends ValidationError(message, null)
    case class NonceError(override val message: String) extends ValidationError(message, null)
    case class InvariantBroken(override val message: String) extends ValidationError(message, null)

    private case class CallbackData(
        full_name: String,
        given_name: String,
        family_name: String,
        social_name: String,
        preferred_username: String,
        email: String,
        picture: String,
        identification: String,
    ) derives Codec


    private class PortalDccImpl[
        F[_]: {
            MonadCancelThrow as F,
            Transactor as xa,
            Clock}
    ](using
        config: PortalDccConfig,
        jwtTokens: JwtTokens[F, CallbackData],
    ) extends PortalDcc[F] {
        override val authUri: F[Uri] = F.pure(config.baseUrl +? ("app", config.appId))

        override def validate(queryParams: Map[String, String]): F[Either[ValidationError, (Account, UcampusLogin)]] =
            (for {
                jwt <- EitherT
                    .fromOption(
                        queryParams.get("jwt"),
                        CallbackFormatError("Missing 'jwt' query parameter."))
                data <- EitherT(jwtTokens.verifyAccessToken(jwt))
                    .leftMap[ValidationError](JwtValidationError.apply)
                mufasaId <- EitherT.fromEither(obtainMufasaId(data.identification))
                // TODO: verify mufasaId?
                ans <- EitherT(
                    UcampusLoginRepository.getOrCreateAccount(
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
