package cl.cadcc.ramitos.repository

import cats.syntax.all.*
import cats.data.{NonEmptyList, OptionT}
import cl.cadcc.ramitos.model.{Course, Review, Stat}
import cl.cadcc.ramitos.model.implicits.given
import doobie.syntax.all.*
import doobie.{ConnectionIO, Fragment}
import fs2.Stream

import scala.language.implicitConversions

trait ReviewRepository {
    def getById(id: Int): ConnectionIO[Option[Review]]
    /**
     * @param withComments `Some(true)` keeps only reviews with a comment, `Some(false)` only reviews without one.
     * @param visible `Some(true)` keeps only reviews whose comment is not hidden by moderation,
     *                `Some(false)` only reviews with a hidden comment.
     */
    def list(limit: Long, courseCode: Option[String] = None, accountId: Option[Int] = None, withComments: Option[Boolean] = None, visible: Option[Boolean] = None, after: Option[Int] = None, order: SqlOrder = SqlOrder.DESCENDING): Stream[ConnectionIO, Review]

    /**
     * Marks the comment as hidden by moderation log entry `logId`.
     * Returns false when the review has no comment or it is already hidden.
     */
    def hideComment(id: Int, logId: Int): ConnectionIO[Boolean]

    /** Returns false when the comment is not hidden. */
    def unhideComment(id: Int): ConnectionIO[Boolean]

    /**
     * This method locks on the course row.
     */
    def create(
        accountId: Int,
        courseCode: String,
        comments: Option[String],
        stats: Map[Stat, Option[Byte]],
        tags: Vector[String],
    ): ConnectionIO[Review]
    def rawCreate(
        accountId: Int,
        courseCode: String,
        comments: Option[String],
        stats: Map[Stat, Option[Byte]],
        tags: Vector[String],
    ): ConnectionIO[Review]
}

object ReviewRepository {

    def apply(using ev: ReviewRepository): ReviewRepository = ev

    def ofCourseRepository(cr: CourseRepositoryOps) : ReviewRepository = ReviewRepositoryImpl(cr)

    private class ReviewRepositoryImpl(courseRepository: CourseRepositoryOps) extends ReviewRepository {
        private val Table = Review.Table
        private val CourseTable = Course.Table

        def getById(id: Int): ConnectionIO[Option[Review]] =
            sql"SELECT ${Table.all} FROM $Table WHERE ${Table.id === id}".query[Review].option

        def list(
            limit: Long,
            courseCode: Option[String] = None,
            accountId: Option[Int] = None,
            withComments: Option[Boolean] = None,
            visible: Option[Boolean] = None,
            after: Option[Int] = None,
            order: SqlOrder = SqlOrder.DESCENDING
        ): Stream[ConnectionIO, Review] = {
            val where = mkWhere(
                courseCode.map(Table.courseCode === _),
                accountId.map(Table.accountId === _),
                after.map(Table.id > _),
                withComments.map {
                    case true => Table.comments.isNotNull
                    case false => Table.comments.isNull
                },
                visible.map {
                    case true => Table.commentHideLogId.isNull
                    case false => Table.commentHideLogId.isNotNull
                },
            )
            val sql =
                fr"SELECT ${Table.all} FROM $Table" ++ where ++
                fr"ORDER BY ${Table.createdAt} ASC"
            sql.query[Review].stream
        }

        // Conditional updates: the WHERE clause makes double hide / unhide a no-op even under concurrency.
        def hideComment(id: Int, logId: Int): ConnectionIO[Boolean] =
            sql"""${Table.updateTable(Table.commentHideLogId --> Some(logId))}
                WHERE ${Table.id === id} AND ${Table.commentHideLogId.isNull} AND ${Table.comments.isNotNull}"""
                .update.run.map(_ == 1)

        def unhideComment(id: Int): ConnectionIO[Boolean] =
            sql"""${Table.updateTable(Table.commentHideLogId --> (None: Option[Int]))}
                WHERE ${Table.id === id} AND ${Table.commentHideLogId.isNotNull}"""
                .update.run.map(_ == 1)

        /**
         * This method locks on the course row.
         */
        def create(
            accountId: Int,
            courseCode: String,
            comments: Option[String],
            stats: Map[Stat, Option[Byte]],
            tags: Vector[String],
        ): ConnectionIO[Review] =
            for {
                course <-
                    OptionT(courseRepository.getByCode(courseCode, forUpdate = true))
                        .getOrRaise(EntityNotFoundException("Course not found"))
                review <- rawCreate(accountId, courseCode, comments, stats, tags)
                updCourse = course.mergeStats(review.stats, review.tags.toSet)
                _ <- courseRepository.updateStats(updCourse.code, updCourse.stats, updCourse.tagStats, comments.isDefined)
            } yield review

        def rawCreate(
            accountId: Int,
            courseCode: String,
            comments: Option[String],
            stats: Map[Stat, Option[Byte]],
            tags: Vector[String],
        ): ConnectionIO[Review] = {
            val upds: NonEmptyList[(Fragment, Fragment)] = NonEmptyList.of(
                Table.accountId --> accountId,
                Table.courseCode --> courseCode,
                Table.comments --> comments,
                Table.tags --> tags,
            )

            val statUpds: List[(Fragment, Fragment)] = stats.toList.map( (stat, value) => Table.stat(stat) --> value)

            Table.insertInto(upds ++ statUpds).update.withUniqueGeneratedKeys[Review](Table.columnNames*)
        }
    }
}
