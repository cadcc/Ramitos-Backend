package  cl.cadcc.ramitos

import cats.effect.{IO, Sync}
import com.comcast.ip4s.{Host, Port}
import org.http4s.Uri
import org.typelevel.log4cats.LoggerFactory
import pureconfig.*
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.derivation.*
import pureconfig.module.catseffect.syntax.*
import pureconfig.module.http4s.*
import pureconfig.module.ip4s.*

import java.util.Properties

case class DbCredentials(
    username: String,
    password: String,
    database: String
) derives ConfigReader

case class HikariConfig(
    maximumPoolSize: Option[Int],
    minimumIdleMillis: Option[Int],
    maxLifetimeMillis: Option[Long],
    idleTimeoutMillis: Option[Long]
) derives ConfigReader

case class DbConfig(
    host: Host,
    port: Port,
    credentials: DbCredentials,
    hikari: HikariConfig
) derives ConfigReader

case class BcryptConfig(
    rounds: Int,
    pepper: Option[String]
) derives ConfigReader

case class DccLogin(
    baseUrl: Uri,
    appName: String
) derives ConfigReader

case class JwtConfig(
    secretKey: String,
    accessTokenLifeSeconds: Long,
    refreshTokenLifeSeconds: Long,
    leewaySeconds: Long
) derives ConfigReader

case class AuthConfig(
    bcrypt: BcryptConfig,
    dccLogin: DccLogin,
    jwt: JwtConfig
) derives ConfigReader

case class HttpConfig(
    host: Host,
    port: Port
) derives ConfigReader

case class MufasaConfig(
    baseUrl: Uri,
    token: String
) derives ConfigReader

case class LogConfig(
    level: Map[String, String]
)

case class RamitosConfig(
    http: HttpConfig,
    db: DbConfig,
    auth: AuthConfig,
    mufasa: MufasaConfig,
    cfg: Map[String, String],
    logging: LogConfig
) derives ConfigReader

object RamitosConfig {
    private val source = ConfigSource.default

    def load[F[_]: Sync](filename: Option[String] = None): F[RamitosConfig] = {
        filename match {
            case Some(f) => ConfigSource.file(f).withFallback(source).loadF()
            case None    => source.loadF()
        }
    }

    // Source - https://stackoverflow.com/a/64400919
    // Posted by Matthias Berndt
    // Retrieved 2026-02-28, License - CC BY-SA 4.0
    implicit val strMapReader: ConfigReader[Map[String, String]] = {
        implicit val r: ConfigReader[String => Map[String, String]] =
            ConfigReader[String]
              .map(v => (prefix: String) => Map(prefix -> v))
              .orElse {
                  strMapReader.map { v =>
                      (prefix: String) => v.map { case (k, v2) => s"$prefix.$k" -> v2 }
                  }
              }
        ConfigReader[Map[String, String => Map[String, String]]].map {
            _.flatMap { case (prefix, v) => v(prefix) }
        }
    }
    // end of copied code
}
