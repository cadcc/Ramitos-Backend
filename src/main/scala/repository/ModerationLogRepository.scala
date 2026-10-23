package cl.cadcc.ramitos.repository

import cats.data.NonEmptySeq
import cl.cadcc.ramitos.model.{ModerationActionType, ModerationLogEntry}
import cl.cadcc.ramitos.model.implicits.given
import cl.cadcc.ramitos.model.implicits.{jsonbGet, jsonbPut}
import doobie.*
import doobie.syntax.all.*
import fs2.Stream

import scala.language.implicitConversions

object ModerationLogRepository {
    val Table = ModerationLogEntry.Table

    def record(actorAccountId: Int, action: ModerationActionType)(
        target: action.Target,
        reason: String,
        details: action.Details,
    ): ConnectionIO[ModerationLogEntry] =
        Table.insertInto(
            NonEmptySeq.of(
                Table.actorAccountId --> actorAccountId,
                Table.action --> action,
                Table.targetReviewId --> action.targetColumns(target),
                Table.reason --> reason,
                Table.details --> action.detailsCodec(details),
            )
        )
            .update
            .withUniqueGeneratedKeys[ModerationLogEntry](Table.columnNames*)

    /** `after` excludes the entry with that id and everything before it in `order`. */
    def list(
        limit: Long,
        reviewId: Option[Int] = None,
        actorAccountId: Option[Int] = None,
        action: Option[ModerationActionType] = None,
        after: Option[Int] = None,
        order: SqlOrder = SqlOrder.DESCENDING,
    ): Stream[ConnectionIO, ModerationLogEntry] = {
        val where = mkWhere(
            reviewId.map(id => Table.targetReviewId === Some(id)),
            actorAccountId.map(Table.actorAccountId === _),
            action.map(Table.action === _),
            after.map(id => order match {
                case SqlOrder.ASCENDING => Table.id > id
                case SqlOrder.DESCENDING => Table.id < id
            }),
        )
        val orderSql = order match {
            case SqlOrder.ASCENDING => fr"ASC"
            case SqlOrder.DESCENDING => fr"DESC"
        }
        val sql =
            fr"SELECT ${Table.all} FROM $Table" ++ where ++
            fr"ORDER BY ${Table.id}" ++ orderSql ++ fr"LIMIT $limit"
        sql.query[ModerationLogEntry].stream
    }
}
