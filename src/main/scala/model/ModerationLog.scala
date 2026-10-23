package cl.cadcc.ramitos.model

import cats.syntax.all.*
import doobie.*
import doobie.postgres.implicits.*
import io.circe.{Codec, Json}
import io.circe.syntax.*

import java.time.Instant
import scala.compiletime.deferred
import implicits.given
import cl.cadcc.ramitos.model.implicits.{jsonbGet, jsonbPut}

/** Details of the review comment actions: a snapshot of the review before the action. */
case class ReviewCommentDetails(courseCode: String, previousHideLogId: Option[Int])

object ReviewCommentDetails {
    given codec: Codec[ReviewCommentDetails] =
        Codec.forProduct2("course_code", "previous_hide_log_id")(ReviewCommentDetails.apply)(d => (d.courseCode, d.previousHideLogId))
}

/**
 * Kind of moderation action. Each kind fixes the type of its target (stored in one of the
 * `target_*_id` columns) and the type of its `details` JSONB payload.
 */
sealed trait ModerationActionType {
    type Target
    type Details

    def s: String

    /** Values for the `target_*_id` columns, in table order. Exactly one is defined. */
    def targetColumns(target: Target): Option[Int]
    def targetOfColumns(reviewId: Option[Int]): Option[Target]

    given detailsCodec: Codec[Details] = deferred
}

object ModerationActionType {
    sealed trait OnReviewComment extends ModerationActionType {
        type Target = Int
        type Details = ReviewCommentDetails

        def targetColumns(reviewId: Int): Option[Int] = reviewId.some
        def targetOfColumns(reviewId: Option[Int]): Option[Int] = reviewId
    }

    case object REVIEW_COMMENT_HIDE extends OnReviewComment {
        val s = "review_comment_hide"
    }

    case object REVIEW_COMMENT_UNHIDE extends OnReviewComment {
        val s = "review_comment_unhide"
    }

    given Meta[ModerationActionType] = pgEnumStringOpt[ModerationActionType]("moderation_action", ofString, _.s)

    def ofString(s: String): Option[ModerationActionType] = s match {
        case "review_comment_hide" => REVIEW_COMMENT_HIDE.some
        case "review_comment_unhide" => REVIEW_COMMENT_UNHIDE.some
        case _ => None
    }
}

case class ModerationLogEntry(
    id: Int,
    actorAccountId: Int,
    action: ModerationActionType,
    target: action.Target,
    details: action.Details,
    reason: String,
    createdAt: Instant
)

object ModerationLogEntry {
    private type Tup = (Int, Int, ModerationActionType, Option[Int], String, Json, Instant)

    given Read[ModerationLogEntry] = Read[Tup].map(toModel.tupled)

    // `action`, `target_review_id` and `details` are decoded together: the action decides the other two types.
    private def toModel(
        id: Int,
        actorAccountId: Int,
        action: ModerationActionType,
        targetReviewId: Option[Int],
        reason: String,
        details: Json,
        createdAt: Instant) = {
        import action.given
        ModerationLogEntry(
            id,
            actorAccountId,
            action,
            // Guaranteed by the moderation_log_one_target CHECK constraint.
            action.targetOfColumns(targetReviewId).getOrElse(
                throw IllegalStateException(s"moderation_log row $id has no target for ${action.s}")),
            details.as[action.Details].fold(
                err => throw IllegalStateException(s"moderation_log row $id has invalid details: ${err.show}"),
                identity),
            reason,
            createdAt
        )
    }

    private def ofModel(m: ModerationLogEntry): Tup = {
        import m.action.given
        (
            m.id, m.actorAccountId, m.action,
            m.action.targetColumns(m.target),
            m.reason,
            m.details.asJson,
            m.createdAt
        )
    }

    object Table extends TableDefinition("moderation_log") {
        lazy val columns = Composite(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

        val id: Column[Int] = Column("id")
        val actorAccountId: Column[Int] = Column("actor_account_id")
        val action: Column[ModerationActionType] = Column("action")
        val targetReviewId: Column[Option[Int]] = Column("target_review_id")
        val reason: Column[String] = Column("reason")
        val details: Column[Json] = Column("details")
        val createdAt: Column[Instant] = Column("created_at")

        object all extends WithSQLDefinition[ModerationLogEntry](Composite((
            id.sqlDef,
            actorAccountId.sqlDef,
            action.sqlDef,
            targetReviewId.sqlDef,
            reason.sqlDef,
            details.sqlDef,
            createdAt.sqlDef
        ))(toModel)(ofModel)) with TableDefinition.RowHelpers[ModerationLogEntry](this)
    }
}
