package cl.cadcc.ramitos.routes

import cats.Monad
import cats.data.OptionT
import cats.effect.MonadCancelThrow
import cats.mtl.Ask
import cats.syntax.all.*
import cats.effect.syntax.all.*
import cl.cadcc.ramitos.utils.extensions.*
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cl.cadcc.ramitos.model.{AccountRole, Stat, Review as ModelReview}
import cl.cadcc.ramitos.repository.ReviewRepository
import cl.cadcc.ramitos.routes.utils.*
import cl.cadcc.ramitos.schema
import cl.cadcc.ramitos.schema.{DuplicatedEntity, InsufficientPermissions, ListReviewsOutput, NotFound, Review, ReviewService, ReviewStats}
import cl.cadcc.ramitos.utils.Shapeless.schemaConvert
import doobie.Transactor
import doobie.syntax.all.*
import smithy4s.time.Timestamp

class ReviewImpl[F[_]: {MonadCancelThrow as F, Transactor as xa}](using session: Ask[F, Session], reviewRepository: ReviewRepository) extends ReviewService[F] {
    private def modelToSchema(m: ModelReview): Review =
        Review(
            id = m.id,
            account_id = m.accountId,
            course_code = m.courseCode,
            comments = m.comments,
            stats = statsToSchema(m.stats),
            tags = m.tags.toList,
            created_at = Timestamp.fromInstant(m.createdAt)
        )

    private def schemaToStats(s: ReviewStats): Map[Stat, Option[Byte]] =
        Map(
            Stat.DOCENCIA -> s.docencia,
            Stat.VIBES -> s.vibes,
            Stat.RELEVANCIA -> s.relevancia,
            Stat.CARGA -> s.carga,
            Stat.DIFICULTAD -> s.dificultad,
        )

    /** HTTP GET /api/reviews */
    override def listReviews(limit: Long, createdOrder: schema.Ordering, courseId: Option[String], accountId: Option[Int], after: Option[Int]): F[ListReviewsOutput] =
        for {
            session <- session.ask
            // Permission check
            _ <- accountId match {
                case Some(accId) if accId == session.account.id => F.unit
                case Some(_) | None => minPermission(AccountRole.MOD)
            }
            reviews <- reviewRepository.list(
                limit,
                courseCode = courseId,
                accountId = accountId,
                after = after,
                order = createdOrder.toModel,
            ).map(modelToSchema).compile.toList.transact(xa)
        } yield ListReviewsOutput(reviews)

    /** HTTP POST /api/reviews */
    override def createReview(course_code: String, stats: ReviewStats, tags: List[String], comments: Option[String]): F[Review] =
        for {
            session <- session.ask
            review <-
                reviewRepository
                    .create(
                        session.account.id,
                        course_code,
                        comments,
                        schemaToStats(stats),
                        tags.toVector )
                    .transact(xa)
                    .exceptSomeSqlState {
                        case doobie.postgres.sqlstate.class23.UNIQUE_VIOLATION =>
                            F.raiseError(DuplicatedEntity(List("course_code")))
                    }
        } yield modelToSchema(review)

    /** HTTP GET /api/reviews/{reviewId} */
    override def getReview(reviewId: Int): F[Review] =
        for {
            session <- session.ask
            review <-
                OptionT(reviewRepository.getById(reviewId).transact(xa))
                    .getOrRaise(NotFound())
            _ <- F.raiseWhen(session.account.id != review.accountId)(InsufficientPermissions())
        } yield modelToSchema(review)
}
