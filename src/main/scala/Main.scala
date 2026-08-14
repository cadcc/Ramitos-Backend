package cl.cadcc.ramitos

import cats.*
import cats.data.NonEmptyList
import cats.effect.*
import cats.effect.std.SystemProperties
import cats.syntax.all.*
import cl.cadcc.ramitos.config.{DbConfig, TagSettings}
import cl.cadcc.ramitos.middleware.AuthMiddleware
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cl.cadcc.ramitos.repository.{CourseRepository, ReviewRepository}
import cl.cadcc.ramitos.routes.restRoutes
import cl.cadcc.ramitos.schema.NotAuthenticated
import cl.cadcc.ramitos.utils.{Crypto, PortalDcc}
import com.zaxxer.hikari.HikariConfig as HikariConfiguration
import doobie.hikari.HikariTransactor
import doobie.util.log.LogHandler
import doobie.util.transactor.Transactor
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.implicits.*
import org.postgresql.ds.PGSimpleDataSource
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

object Main extends IOApp {
    given logging: LoggerFactory[IO] = Slf4jFactory.create[IO]
    private val mainLogger = logging.getLogger

    private def getTransactor(config: DbConfig): Resource[IO, Transactor[IO]] =
        val datasource = PGSimpleDataSource()
        datasource.setServerNames(Array(config.host.show))
        datasource.setPortNumbers(Array(config.port.value))
        datasource.setUser(config.credentials.username)
        datasource.setPassword(config.credentials.password)
        datasource.setDatabaseName(config.credentials.database)
        
        val hikari = HikariConfiguration()
        hikari.setDataSource(datasource)

        config.hikari.idleTimeoutMillis.foreach( hikari.setIdleTimeout )
        config.hikari.maxLifetimeMillis.foreach( hikari.setMaxLifetime )
        config.hikari.maximumPoolSize  .foreach( hikari.setMaximumPoolSize )
        config.hikari.minimumIdleMillis.foreach( hikari.setMinimumIdle )

        HikariTransactor.fromHikariConfig(hikari, logHandler = LogHandler.jdkLogHandler[IO].some)
    
    private val resources: Resource[IO, RamitosContext[IO]] =
        for {
            configFile <- SystemProperties[IO].get("ramitos.configFile").toResource
            conf <- RamitosConfig.load[IO](configFile).toResource
            xa <- getTransactor(conf.db)
            given Transactor[IO] = xa
            crypto <- Crypto.ofConf(conf.auth.bcrypt).pure[ResourceIO]
            jwt <- JwtTokens.ofClock[IO, Session](conf.auth.jwt).pure[ResourceIO]
            portalDcc <- PortalDcc.ofConf[IO](conf.auth.portalDcc).pure[ResourceIO]
            auth <- AuthMiddleware.ofJwtTokens(using jwt).toResource
            client <- EmberClientBuilder.default[IO].build
            courseRepository = CourseRepository.ofConf(conf.app.tags)
            reviewRepository = ReviewRepository.ofCourseRepository(courseRepository)
        } yield RamitosContext(xa, conf, auth, logging, client, crypto, jwt, courseRepository, reviewRepository, portalDcc)

    override def run(args: List[String]): IO[ExitCode] =
        (for {
            ctx <- resources
            given RamitosContext[IO] = ctx
            routes <- restRoutes
            server <- EmberServerBuilder.default[IO]
                .withHost(ctx.config.http.host)
                .withPort(ctx.config.http.port)
                .withHttpApp(routes.orNotFound)
                .withErrorHandler({
                    case e @ NotAuthenticated(reason, message) =>
                        Unauthorized(
                            `WWW-Authenticate`(NonEmptyList.one(Challenge("Bearer", "ramitos"))),
                            e.asJson)
                    case t =>
                        mainLogger.error(t)("Internal Server Error")
                        *> MonadThrow[IO].raiseError(t)
                })
                .build
         } yield server)
        .use { _ => IO.never }
        .as(ExitCode.Success)
}
