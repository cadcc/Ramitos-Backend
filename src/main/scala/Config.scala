package  cl.cadcc.ramitos

import cats.MonadError
import cats.syntax.all.*
import cats.effect.{IO, Sync}
import org.http4s.Uri
import pureconfig.*
import pureconfig.error.{ConfigReaderFailures, ConvertFailure, UserValidationFailed}
import pureconfig.generic.derivation.*
import pureconfig.module.catseffect.syntax.*
import pureconfig.module.http4s.*
import pureconfig.module.ip4s.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import org.http4s.Uri.{Host => h4sHost}
import org.http4s.Uri.Scheme
import org.http4s.Uri.Authority

object config {
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

    case class JwtConfig(
        secretKey: String,
        accessTokenLifeSeconds: Long,
        refreshTokenLifeSeconds: Long,
        leewaySeconds: Long
    ) derives ConfigReader

    case class PortalDccConfig(
        baseUrl: Uri,
        appId: String,
        signingKey: String,
    ) derives ConfigReader

    case class DccLoginConfig(
        loginTimeLimitSeconds: Long,
    ) derives ConfigReader

    case class AuthConfig(
        bcrypt: BcryptConfig,
        portalDcc: PortalDccConfig,
        dccLogin: DccLoginConfig,
        jwt: JwtConfig
    ) derives ConfigReader

    case class HttpConfig(
        host: Host,
        port: Port,
        ssl : Boolean,
    ) derives ConfigReader {
        val scheme: Scheme = if ssl then Scheme.https else Scheme.http
        val authority: Authority = Authority(
            host = h4sHost.fromIp4sHost(host),
            port = port.value.some
        )
        val baseUri: Uri = Uri(
            scheme = scheme.some,
            authority = authority.some,
        )
    }

    case class MufasaConfig(
        baseUrl: Uri,
        token: String
    ) derives ConfigReader

    sealed trait TagSetting

    case class SingleTag(id: String) extends TagSetting

    case class ExclusiveSet(ex: Set[String]) extends TagSetting derives ConfigReader

    given ConfigReader[TagSetting] = ConfigReader.fromCursor { cursor =>
        val F = MonadError[ConfigReader.Result, ConfigReaderFailures]
        val keys = Map(
            "ex" -> ExclusiveSet.apply
        )
        val singleTag = cursor.asString.map(SingleTag.apply)
        val set =
            for {
                obj <- cursor.asObjectCursor
                _ <- F.raiseWhen(obj.keys.size != 1)(
                    ConfigReaderFailures(ConvertFailure(UserValidationFailed("A TagSetting must have at most 1 key, or be a String."), obj)))
                key = obj.keys.last
                app <- keys.get(key).toRight(
                    ConfigReaderFailures(ConvertFailure(UserValidationFailed(s"Unknown tag set, available tag sets are: $keys"), obj)))
                keyCursor <- obj.atKey(key)
                listCursor <- keyCursor.asList
                list <- listCursor.traverse(_.asString)
            } yield app(list.toSet)
        singleTag orElse set
    }

    case class TagSettings(settings: Seq[TagSetting]) {
        val allTags: Set[String] = settings.flatMap {
            case SingleTag(id) => Seq(id)
            case ExclusiveSet(ids) => ids
        }.toSet

        val definitions: Map[String, TagSetting] = settings.flatMap {
            case setting@SingleTag(id) => Set(id -> setting)
            case setting@ExclusiveSet(set) => set.map(id => id -> setting)
        }.toMap
    }

    given ConfigReader[TagSettings] = ConfigReader[List[TagSetting]].emap { settings =>
        val allSet = settings.flatMap {
            case SingleTag(id) => List(id)
            case ExclusiveSet(ids) => ids.toList
        }
        if allSet.distinct.size == allSet.size then TagSettings(settings).asRight
        else UserValidationFailed("All tags in the settings must be unique.").asLeft
    }

    case class AppConfig(
        tags: TagSettings
    ) derives ConfigReader
}

case class RamitosConfig(
    http: config.HttpConfig,
    db: config.DbConfig,
    auth: config.AuthConfig,
    mufasa: config.MufasaConfig,
    app: config.AppConfig,
) derives ConfigReader

object RamitosConfig {
    private val source = ConfigSource.default

    def load[F[_]: Sync](filename: Option[String] = None): F[RamitosConfig] = {
        filename match {
            case Some(f) => ConfigSource.file(f).withFallback(source).loadF()
            case None    => source.loadF()
        }
    }
}
