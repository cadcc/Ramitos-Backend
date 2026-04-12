package cl.cadcc.ramitos

import doobie.util.transactor.Transactor
import cats.effect.syntax.all._
import cats.effect.implicits._
import cats.effect.IO
import cats.implicits._
import cats.syntax.all._
import cats.effect.Resource
import cats.mtl.Ask
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cl.cadcc.ramitos.middleware.AuthMiddleware
import org.typelevel.log4cats.LoggerFactory
import org.http4s.client.Client
import cl.cadcc.ramitos.utils.Crypto

case class RamitosContext[F[_]](
    val xa: Transactor[F],
    val config: RamitosConfig,
    val auth: AuthMiddleware[F, Session],
    val logging: LoggerFactory[F],
    val httpClient: Client[F],
    val crypto: Crypto,
    val jwt: JwtTokens[F, Session]
)

object RamitosContext {

    def apply[F[_]](using ev: RamitosContext[F]): RamitosContext[F] = ev
    
    def xa[F[_]](using ev: Transactor[F]): Transactor[F] = ev
    def logging[F[_]](using ev: RamitosContext[F]): LoggerFactory[F] = ev.logging
    def httpClient[F[_]](using ev: RamitosContext[F]): Client[F] = ev.httpClient
    
    given transactorFromContext[F[_]](using ctx: RamitosContext[F]): Transactor[F] = ctx.xa
    given clientFromContext[F[_]](using ctx: RamitosContext[F]): Client[F] = ctx.httpClient
    given jwtTokensFromContext[F[_]](using ctx: RamitosContext[F]): JwtTokens[F, Session] = ctx.jwt
    given cryptoFromContext[F[_]](using ctx: RamitosContext[F]): Crypto = ctx.crypto
}
