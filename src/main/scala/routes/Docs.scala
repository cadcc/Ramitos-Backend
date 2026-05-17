package cl.cadcc.ramitos.routes

import cats.effect.Sync
import cl.cadcc.ramitos.RamitosContext
import cl.cadcc.ramitos.RamitosContext.given
import cl.cadcc.ramitos.schema.{AccountService, AnonymousReviewService, AuthenticationService, CourseService, ReviewService, WoofService}
import org.http4s.HttpRoutes

object Docs {
    def docs[F[_]: Sync]: HttpRoutes[F] = smithy4s.http4s.swagger.docs[F](
        AccountService,
        AnonymousReviewService,
        AuthenticationService,
        CourseService,
        ReviewService,
        WoofService,
    )
}
