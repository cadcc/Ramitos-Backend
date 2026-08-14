package cl.cadcc.ramitos

import doobie.util.transactor.Transactor
import cats.effect.syntax.all.*
import cats.effect.implicits.*
import cats.effect.IO
import cats.implicits.*
import cats.syntax.all.*
import cats.effect.Resource
import cats.mtl.Ask
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cl.cadcc.ramitos.middleware.AuthMiddleware
import cl.cadcc.ramitos.repository.{CourseRepository, ReviewRepository}
import org.typelevel.log4cats.LoggerFactory
import org.http4s.client.Client
import cl.cadcc.ramitos.utils.Crypto
import cl.cadcc.ramitos.utils.PortalDcc

case class RamitosContext[F[_]](
    xa: Transactor[F],
    config: RamitosConfig,
    auth: AuthMiddleware[F, Session],
    logging: LoggerFactory[F],
    httpClient: Client[F],
    crypto: Crypto,
    jwt: JwtTokens[F, Session],
    courseRepository: CourseRepository,
    reviewRepository: ReviewRepository,
    portalDcc: PortalDcc[F],
)

object RamitosContext {

    def apply[F[_]](using ev: RamitosContext[F]): RamitosContext[F] = ev
    
    def xa[F[_]](using ev: Transactor[F]): Transactor[F] = ev
    def logging[F[_]](using ev: RamitosContext[F]): LoggerFactory[F] = ev.logging
    def httpClient[F[_]](using ev: RamitosContext[F]): Client[F] = ev.httpClient
    
    given loggingFromContext[F[_]](using ctx: RamitosContext[F]): LoggerFactory[F] = ctx.logging
    given transactorFromContext[F[_]](using ctx: RamitosContext[F]): Transactor[F] = ctx.xa
    given clientFromContext[F[_]](using ctx: RamitosContext[F]): Client[F] = ctx.httpClient
    given jwtTokensFromContext[F[_]](using ctx: RamitosContext[F]): JwtTokens[F, Session] = ctx.jwt
    given cryptoFromContext[F[_]](using ctx: RamitosContext[F]): Crypto = ctx.crypto
    given courseRepositoryFromContext[F[_]](using ctx: RamitosContext[F]): CourseRepository = ctx.courseRepository
    given reviewRepositoryFromContext[F[_]](using ctx: RamitosContext[F]): ReviewRepository = ctx.reviewRepository
    given dccPortalFromContext[F[_]](using ctx: RamitosContext[F]): PortalDcc[F] = ctx.portalDcc
    given askSessionFromContext[F[_]](using ctx: RamitosContext[F]): Ask[F, Session] = ctx.auth.askSession
}
