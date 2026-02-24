package cl.cadcc.ramitos.repository

import cats._, cats.syntax.all._
import doobie._, doobie.syntax.all._, doobie.implicits._
import fs2.Stream
import cl.cadcc.ramitos.model.Course
import cl.cadcc.ramitos.model.Stat
import cats.effect.kernel.MonadCancel
import cats.data.OptionT
import cats.effect.kernel.Sync
import cl.cadcc.ramitos.model.CourseStat
import java.time.Instant

object CourseRepository {
    private val Table = Course.Table

    def getByCode(code: String, forUpdate: Boolean = false): ConnectionIO[Option[Course]] =
        val sql =
               fr"SELECT $Table.columns FROM $Table WHERE ${Table.code === code}"
            ++ (if forUpdate then fr"FOR UPDATE" else fr"")

        sql.query[Course]
            .option

    def list(limit: Long, from: Option[String]): Stream[ConnectionIO, Course] =
        val sql =
               fr"SELECT $Table.columns FROM $Table "
            ++ (from match
                case Some(value) => fr"WHERE ${ Table.code < value }"
                case None => fr"")
            ++ fr"GROUP BY $Table.code LIMIT $limit"
        sql.query[Course].stream
    
    def create(code: String, name: String): ConnectionIO[Course] =
        Table.insertInto(
            Table.code --> code,
            Table.displayName --> name
        )
            .update
            .withUniqueGeneratedKeys(Table.columnNames*)
    
    def updateStat(code: String, stat: Stat, newVal: CourseStat, now: Instant): ConnectionIO[Boolean] =
        sql"${Table.updateTable(
            Table.createdAt --> now,
            (Table.getStat(stat) ==> newVal).toVector*
        )} WHERE ${Table.code === code}"
            .update
            .run
            .map(_ == 1)
    
    private[repository] def addStatValue(code: String, stat: Stat, newVal: Int, now: Instant): ConnectionIO[Float] =
        for {
            course <- OptionT(getByCode(code, forUpdate = true)).getOrRaise(new EntityNotFoundException(s"No course with code [$code]"))
            statDef = course.getStat(stat)
            newCount = statDef.count + 1
            newSum = statDef.sum + newVal
            newRate = newSum / newSum
            newStat = CourseStat(
                rate = newRate,
                count = newCount,
                sum = newSum
            )
            ans <- updateStat(code, stat, newStat, now)
            _ <- MonadThrow[ConnectionIO].raiseUnless(ans)(new AssertionError("No update after selecting entity. This should never happen!"))
        } yield newRate
}
