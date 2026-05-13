package cl.cadcc.ramitos.routes

import cats.data.OptionT
import cats.syntax.all.*
import cats.effect.Concurrent
import cl.cadcc.ramitos.model.Review as ModelReview
import cl.cadcc.ramitos.repository.{CourseRepository, ReviewRepository}
import cl.cadcc.ramitos.routes.utils.*
import cl.cadcc.ramitos.schema.{AnonymousReview, AnonymousReviewService, ListCourseReviewsOutput, NotFound}
import doobie.Transactor
import doobie.syntax.all.*
import smithy4s.time.Timestamp

class AnonymousReviewImpl[F[_]: {Concurrent as F, Transactor as xa}](using reviewRepository: ReviewRepository, courseRepository: CourseRepository) extends AnonymousReviewService[F] {
    private def anonymize(m: ModelReview): AnonymousReview =
        AnonymousReview(
            id = m.id,
            comments = m.comments.get,
            stats = statsToSchema(m.stats),
            tags = m.tags.toList,
            created_at = Timestamp.fromInstant(m.createdAt),
        )

    /** HTTP GET /api/courses/{courseCode}/reviews */
    override def listCourseReviews(courseCode: String, limit: Long, after: Option[Int]): F[ListCourseReviewsOutput] =
        for {
            course <- OptionT(courseRepository.getByCode(courseCode).transact(xa)).getOrRaise(NotFound())
            ans <- reviewRepository.list(limit, courseCode = courseCode.some, withComments = true.some, after = after)
                .transact(xa)
                .map(anonymize)
                .compile
                .toList
        } yield ListCourseReviewsOutput(ans)
}
