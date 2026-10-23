package cl.cadcc.ramitos.model

import cats.syntax.all.*
import doobie.*
import doobie.postgres.implicits.*
import io.circe.Json
import cl.cadcc.ramitos.schema.{ModerationActionType as SchemaActionType, ModerationTarget as SchemaTarget}
import cl.cadcc.ramitos.utils.Shapeless.SchemaModelConvert

import java.time.Instant
import implicits.given
import cl.cadcc.ramitos.model.implicits.{jsonbGet, jsonbPut}

enum ModerationActionType(val s: String) {
    case REVIEW_COMMENT_HIDE extends ModerationActionType("review_comment_hide")
    case REVIEW_COMMENT_UNHIDE extends ModerationActionType("review_comment_unhide")
}

object ModerationActionType {
    given Meta[ModerationActionType] = pgEnumStringOpt[ModerationActionType]("moderation_action", ofString, _.s)

    def ofString(s: String): Option[ModerationActionType] = s match {
        case "review_comment_hide" => REVIEW_COMMENT_HIDE.some
        case "review_comment_unhide" => REVIEW_COMMENT_UNHIDE.some
        case _ => None
    }
}

given moderationActionConvert: SchemaModelConvert[ModerationActionType, SchemaActionType] = SchemaModelConvert.instance {
    case ModerationActionType.REVIEW_COMMENT_HIDE => SchemaActionType.REVIEW_COMMENT_HIDE
    case ModerationActionType.REVIEW_COMMENT_UNHIDE => SchemaActionType.REVIEW_COMMENT_UNHIDE
}

/** Target of a moderation action. Each case maps to one `target_*_id` column. */
enum ModerationTarget {
    case Review(id: Int)
}

object ModerationTarget {
    /** Values for the `target_*_id` columns, in table order. Exactly one is defined. */
    def columns(t: ModerationTarget): Option[Int] = t match {
        case Review(id) => id.some
    }

    def ofColumns(reviewId: Option[Int]): Option[ModerationTarget] = reviewId match {
        case Some(id) => Review(id).some
        case None => None
    }
}

given moderationTargetConvert: SchemaModelConvert[ModerationTarget, SchemaTarget] = SchemaModelConvert.instance {
    case ModerationTarget.Review(id) => SchemaTarget.review(id)
}

case class ModerationLogEntry(
    id: Int,
    actorAccountId: Int,
    action: ModerationActionType,
    target: ModerationTarget,
    reason: String,
    details: Json,
    createdAt: Instant
)

object ModerationLogEntry {
    private type Tup = (Int, Int, ModerationActionType, Option[Int], String, Json, Instant)

    given Read[ModerationLogEntry] = Read[Tup].map(toModel.tupled)

    private def toModel(
        id: Int,
        actorAccountId: Int,
        action: ModerationActionType,
        targetReviewId: Option[Int],
        reason: String,
        details: Json,
        createdAt: Instant) =
        ModerationLogEntry(
            id,
            actorAccountId,
            action,
            // Guaranteed by the moderation_log_one_target CHECK constraint.
            ModerationTarget.ofColumns(targetReviewId).getOrElse(
                throw IllegalStateException(s"moderation_log row $id has no target")),
            reason,
            details,
            createdAt
        )

    private def ofModel(m: ModerationLogEntry): Tup =
        (m.id, m.actorAccountId, m.action, ModerationTarget.columns(m.target), m.reason, m.details, m.createdAt)

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
