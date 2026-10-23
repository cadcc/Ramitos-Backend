package cl.cadcc.ramitos.repository

import cats.data.NonEmptySeq
import cl.cadcc.ramitos.model.{ModerationActionType, ModerationLogEntry, ModerationTarget}
import cl.cadcc.ramitos.model.implicits.given
import cl.cadcc.ramitos.model.implicits.{jsonbGet, jsonbPut}
import doobie.*
import doobie.syntax.all.*
import fs2.Stream
import io.circe.Json

import scala.language.implicitConversions

object ModerationLogRepository {
    val Table = ModerationLogEntry.Table

    def record(
        actorAccountId: Int,
        action: ModerationActionType,
        target: ModerationTarget,
        reason: String,
        details: Json,
    ): ConnectionIO[ModerationLogEntry] =
        Table.insertInto(
            NonEmptySeq.of(
                Table.actorAccountId --> actorAccountId,
                Table.action --> action,
                Table.targetReviewId --> ModerationTarget.columns(target),
                Table.reason --> reason,
                Table.details --> details,
            )
        )
            .update
            .withUniqueGeneratedKeys[ModerationLogEntry](Table.columnNames*)

    /** Newest first. `before` is an exclusive upper bound on the entry id. */
    def list(
        limit: Long,
        reviewId: Option[Int] = None,
        actorAccountId: Option[Int] = None,
        action: Option[ModerationActionType] = None,
        before: Option[Int] = None,
    ): Stream[ConnectionIO, ModerationLogEntry] = {
        val where = mkWhere(
            reviewId.map(id => Table.targetReviewId === Some(id)),
            actorAccountId.map(Table.actorAccountId === _),
            action.map(Table.action === _),
            before.map(Table.id < _),
        )
        val sql =
            fr"SELECT ${Table.all} FROM $Table" ++ where ++
            fr"ORDER BY ${Table.id} DESC LIMIT $limit"
        sql.query[ModerationLogEntry].stream
    }
}
