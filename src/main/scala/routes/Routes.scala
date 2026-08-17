package cl.cadcc.ramitos.routes

import cats.syntax.all._, cats.effect.syntax.all._
import cats.effect.IO
import cats.effect.kernel.Resource
import org.http4s.HttpRoutes
import alloy.SimpleRestJson
import smithy4s.http4s.SimpleRestJsonBuilder
import smithy4s.kinds.FunctorAlgebra
import cl.cadcc.ramitos.RamitosContext
import cl.cadcc.ramitos.RamitosContext.given
import smithy4s.Service
import cats.effect.Concurrent
import cl.cadcc.ramitos.routes.Authentication
import org.typelevel.log4cats.LoggerFactory
import cl.cadcc.ramitos.middleware.RedirectMiddleware
import cl.cadcc.ramitos.config.HttpConfig
import cl.cadcc.ramitos.config.DccLoginConfig


def restRoutes(using ctx: RamitosContext[IO]): Resource[IO, HttpRoutes[IO]] =
    val redir = RedirectMiddleware.of[IO]

    def makeRoutes[Alg[_[_,_,_,_,_]]](impl: FunctorAlgebra[Alg, IO])(using ctx: RamitosContext[IO])(using Service[Alg]) =
        val logger = ctx.logging.getLoggerFromClass(impl.getClass())
        SimpleRestJsonBuilder
            .routes(impl)
            .middleware(ctx.auth.middleware andThen redir)
            .resource

    given HttpConfig = ctx.config.http
    given DccLoginConfig = ctx.config.auth.dccLogin

    for {
        strm <- Resource.pure(RawStreamImpl.routes[IO])
        woof <- makeRoutes(WoofImpl[IO])
        authAlg <- Authentication.ofAsync[IO].toResource
        auth <- makeRoutes(authAlg)
        acc  <- makeRoutes(AccountImpl[IO])
        cour <- makeRoutes(CourseImpl[IO])
        rew  <- makeRoutes(ReviewImpl[IO])
        arew <- makeRoutes(AnonymousReviewImpl[IO])
        docs <- Resource.pure(Docs.docs[IO])
    } yield woof
        <+> strm
        <+> auth
        <+> acc
        <+> cour
        <+> rew
        <+> arew
        <+> docs
