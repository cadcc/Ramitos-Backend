package cl.cadcc.ramitos.repository

import cats.*
import cats.syntax.all.*
import doobie.*
import doobie.syntax.all.*
import doobie.implicits.*
import fs2.Stream
import cl.cadcc.ramitos.model.Course
import cl.cadcc.ramitos.model.Stat
import cats.effect.{Clock, MonadCancel, Sync}
import cats.data.{NonEmptyVector, OptionT}
import cl.cadcc.ramitos.TagSettings
import cl.cadcc.ramitos.model.CourseStat

import java.time.Instant
import scala.language.implicitConversions

trait CourseRepository {
    def getByCode(code: String, forUpdate: Boolean = false): ConnectionIO[Option[Course]]
    def list(limit: Long, from: Option[String]): Stream[ConnectionIO, Course]
    def create(code: String, name: String): ConnectionIO[Course]
    def updateStats(code: String, stats: Map[Stat, CourseStat], tagStats: Map[String, CourseStat]): ConnectionIO[Boolean]
}

object CourseRepository {

    def apply(using ev: CourseRepository): CourseRepository = ev

    def ofConf(tagSettings: TagSettings): CourseRepository = CourseRepositoryImpl(tagSettings)

    private class CourseRepositoryImpl(private val tagSettings: TagSettings) extends CourseRepository {
        private val F = WeakAsync[ConnectionIO]
        private val Table = Course.Table
        private val tagsList: List[String] = tagSettings.allTags.toList

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
                    case Some(value) => fr"WHERE ${Table.code < value}"
                    case None => fr"")
                ++
                fr"ORDER BY $Table.code LIMIT $limit"
            sql.query[Course].stream

        def create(code: String, name: String): ConnectionIO[Course] = {
            val tagStats = tagsList
                .map { tagName =>
                    tagName -> CourseStat(Float.NaN, 0, 0L) }
                .toMap

            Table.insertInto(NonEmptyVector.of(
                    Table.code --> code,
                    Table.displayName --> name,
                    Table.tagStats --> tagStats ))
                .update
                .withUniqueGeneratedKeys(Table.columnNames*)
        }

        def updateStats(code: String, stats: Map[Stat, CourseStat], tagStats: Map[String, CourseStat]): ConnectionIO[Boolean] =
            for {
                now <- F.realTimeInstant
                sql =
                    sql"${
                        Table.updateTable(
                            Table.updatedAt --> now,
                            Table.stats --> stats,
                            Table.tagStats --> tagStats,
                        )
                    } WHERE ${Table.code === code}"
                ans <- sql.update.run.map(_ == 1)
            } yield ans
    }
}
