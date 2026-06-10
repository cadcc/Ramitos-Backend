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
import cl.cadcc.ramitos.routes.Authentication.AuthenticationImpl
import org.typelevel.log4cats.LoggerFactory

private def makeRoutes[Alg[_[_,_,_,_,_]], F[_]](impl: FunctorAlgebra[Alg, F])(using ctx: RamitosContext[F])(using Service[Alg], Concurrent[F]) =
    val logger = ctx.logging.getLoggerFromClass(impl.getClass())
    SimpleRestJsonBuilder
        .routes(impl)
        .middleware(ctx.auth.middleware)
        .resource

def restRoutes(using ctx: RamitosContext[IO]): Resource[IO, HttpRoutes[IO]] =
    for {
//        strm <-
        woof <- makeRoutes(WoofImpl[IO])
        auth <- makeRoutes(AuthenticationImpl[IO])
        acc  <- makeRoutes(AccountImpl[IO])
        cour <- makeRoutes(CourseImpl[IO])
        rew  <- makeRoutes(ReviewImpl[IO])
        arew <- makeRoutes(AnonymousReviewImpl[IO])
        docs <- Resource.pure(Docs.docs[IO])
    } yield woof
        <+> auth
        <+> acc
        <+> cour
        <+> rew
        <+> arew
        <+> docs
