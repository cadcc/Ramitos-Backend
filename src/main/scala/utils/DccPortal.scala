package cl.cadcc.ramitos.utils

import cats._, cats.syntax.all._
import cats.effect.syntax.all._

import cl.cadcc.ramitos.DccLogin
import cats.MonadThrow
import org.http4s.client.Client
import cats.Monad
import cats.effect.Concurrent
import org.typelevel.log4cats.{Logger, LoggerFactory}
import org.http4s.Method
import org.http4s.Request
import org.http4s.syntax.all.uri
import org.http4s.circe._
import org.http4s.UrlForm
import cats.effect.IO
import io.circe.generic.auto._
import org.http4s.EntityDecoder
import io.circe.Codec
import io.circe.Json
import io.circe.KeyDecoder.decodeKeyString
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.Uri
import org.http4s.Query
import io.circe.Decoder
import io.circe.DecodingFailure

trait DccPortal[F[_]] {
    def getUser(username: String, secret: String): F[Option[DccPortal.PortalUser]]
}

object DccPortal {

    def apply[F[_]](using ev: DccPortal[F]): DccPortal[F] = ev

    case class PortalPersona(email: Option[String], alias: Option[String], foto: Option[Uri]) derives Codec

    case class PortalUser(
        username: String,
        email: Option[String],
        first_name: String,
        last_name: String,
        persona: Option[PortalPersona],
    ) derives Codec

    private given Decoder[Option[PortalUser]] = 
        Decoder.instance[Option[PortalUser]] { c => 
            c.get[Boolean]("valid").flatMap { b =>
                if b then c.as[PortalUser].map(Some.apply)
                else None.asRight
            }
        }

    def ofConcurrent[F[_] : {Concurrent, LoggerFactory}](client: Client[F], conf: DccLogin): DccPortal[F] =
        DccPortalImpl[F](client, conf)

    private case class Credentials(username: String, secret: String) derives Codec

    private class DccPortalImpl[F[_] : {Concurrent as F, LoggerFactory}](client: Client[F], conf: DccLogin) extends DccPortal[F] {
        private val logger: Logger[F] = LoggerFactory[F].getLogger


        private val SSO_URL: Uri = conf.baseUrl
        private val SSO_APP = conf.appName
        private val SSO_AUTH = "DJANGO_SSO_AUTH"
        private val withPath: Uri = SSO_URL.withPath("/is_valid")

        def getUser(username: String, secret: String): F[Option[PortalUser]] =
            val withQuery: Uri = withPath
                .withQueryParam("app", SSO_APP)
                .withQueryParam("username", username)
                .withQueryParam("secret", secret)
            client.get(withQuery) { res => 
                res.bodyText
                .compile
                .onlyOrError
                .flatMap { s => logger.info(s"Got response $s") }
                *> res.decodeJson[Option[PortalUser]]
            }
    }
}
