package cl.cadcc.ramitos.routes

import cats.MonadThrow
import cats.data.OptionT
import cats.effect.MonadCancelThrow
import cats.mtl.Ask
import cats.syntax.all.*
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cl.cadcc.ramitos.model.{AccountRole, ModerationLogEntry, ModerationTarget, ModerationActionType as ModelActionType, given}
import cl.cadcc.ramitos.repository.{CourseRepository, ModerationLogRepository, ReviewRepository}
import cl.cadcc.ramitos.routes.utils.*
import cl.cadcc.ramitos.schema.{InvalidModerationState, ListModerationActionsOutput, ModerationAction, ModerationActionType, ModerationService, NotFound}
import cl.cadcc.ramitos.utils.extensions.*
import doobie.{ConnectionIO, Transactor}
import doobie.syntax.all.*
import io.circe.Json
import io.circe.syntax.*
import smithy4s.Document
import smithy4s.time.Timestamp

class ModerationImpl[F[_]: {MonadCancelThrow as F, Transactor as xa}](using session: Ask[F, Session], reviewRepository: ReviewRepository, courseRepository: CourseRepository[F]) extends ModerationService[F] {
    private val ConnIO = MonadThrow[ConnectionIO]

    private def jsonToDocument(json: Json): Document =
        json.fold(
            Document.nullDoc,
            Document.fromBoolean,
            n => Document.fromBigDecimal(n.toBigDecimal.getOrElse(BigDecimal(n.toDouble))),
            Document.fromString,
            arr => Document.array(arr.map(jsonToDocument)),
            obj => Document.obj(obj.toList.map((k, v) => k -> jsonToDocument(v))*),
        )

    private def modelToSchema(m: ModerationLogEntry): ModerationAction =
        ModerationAction(
            id = m.id,
            actor_account_id = m.actorAccountId,
            action = m.action.toSchema,
            target = m.target.toSchema,
            reason = m.reason,
            details = jsonToDocument(m.details),
            created_at = Timestamp.fromInstant(m.createdAt),
        )

    /**
     * Records `action` and applies `change` to the review in one transaction.
     * `change` gets the new log entry id and returns false when the review is in the wrong state,
     * which rolls the whole transaction back (including the log entry).
     */
    private def moderateReviewComment(
        reviewId: Int,
        reason: String,
        action: ModelActionType,
        commentCountDelta: Int,
        invalidState: String,
    )(change: Int => ConnectionIO[Boolean]): F[ModerationAction] =
        for {
            _ <- minPermission(AccountRole.MOD)
            session <- session.ask
            entry <- (for {
                review <- OptionT(reviewRepository.getById(reviewId)).getOrRaise(NotFound())
                // Same lock order as ReviewRepository.create: course row first.
                _ <- courseRepository.getByCode(review.courseCode, forUpdate = true)
                entry <- ModerationLogRepository.record(
                    session.account.id,
                    action,
                    ModerationTarget.Review(reviewId),
                    reason,
                    Json.obj(
                        "course_code" -> review.courseCode.asJson,
                        "previous_hide_log_id" -> review.commentHideLogId.asJson,
                    ),
                )
                changed <- change(entry.id)
                _ <- ConnIO.raiseUnless(changed)(InvalidModerationState(invalidState))
                _ <- courseRepository.adjustCommentCount(review.courseCode, commentCountDelta)
            } yield entry).transact(xa)
        } yield modelToSchema(entry)

    /** HTTP POST /api/moderation/reviews/{reviewId}/hide */
    override def hideReviewComment(reason: String, reviewId: Int): F[ModerationAction] =
        moderateReviewComment(reviewId, reason, ModelActionType.REVIEW_COMMENT_HIDE, -1,
            "The review has no comment or it is already hidden.")(
            logId => reviewRepository.hideComment(reviewId, logId))

    /** HTTP POST /api/moderation/reviews/{reviewId}/unhide */
    override def unhideReviewComment(reason: String, reviewId: Int): F[ModerationAction] =
        moderateReviewComment(reviewId, reason, ModelActionType.REVIEW_COMMENT_UNHIDE, 1,
            "The review comment is not hidden.")(
            _ => reviewRepository.unhideComment(reviewId))

    /** HTTP GET /api/moderation/actions */
    override def listModerationActions(limit: Long, reviewId: Option[Int], actorId: Option[Int], action: Option[ModerationActionType], before: Option[Int]): F[ListModerationActionsOutput] =
        for {
            _ <- minPermission(AccountRole.MOD)
            entries <- ModerationLogRepository.list(
                limit,
                reviewId = reviewId,
                actorAccountId = actorId,
                action = action.map {
                    case ModerationActionType.REVIEW_COMMENT_HIDE => ModelActionType.REVIEW_COMMENT_HIDE
                    case ModerationActionType.REVIEW_COMMENT_UNHIDE => ModelActionType.REVIEW_COMMENT_UNHIDE
                },
                before = before,
            ).map(modelToSchema).compile.toList.transact(xa)
        } yield ListModerationActionsOutput(entries)
}
