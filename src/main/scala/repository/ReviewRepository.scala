package cl.cadcc.ramitos.repository

import cats.syntax.all.*
import cats.data.{NonEmptyList, OptionT}
import cl.cadcc.ramitos.model.{Course, Review, Stat}
import doobie.syntax.all.*
import doobie.{ConnectionIO, Fragment}
import fs2.Stream

import scala.language.implicitConversions

trait ReviewRepository {
    def getById(id: Int): ConnectionIO[Option[Review]]
    def list(limit: Long, courseCode: Option[String] = None, accountId: Option[Int] = None, withComments: Option[Boolean] = None, after: Option[Int] = None, order: SqlOrder = SqlOrder.DESCENDING): Stream[ConnectionIO, Review]

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

    def ofCourseRepository(cr: CourseRepository) : ReviewRepository = ReviewRepositoryImpl(cr)

    private class ReviewRepositoryImpl(courseRepository: CourseRepository) extends ReviewRepository {
        private val Table = Review.Table
        private val CourseTable = Course.Table

        def getById(id: Int): ConnectionIO[Option[Review]] =
            sql"SELECT * FROM $Table WHERE ${Table.id === id}".query[Review].option

        def list(
            limit: Long,
            courseCode: Option[String] = None,
            accountId: Option[Int] = None,
            withComments: Option[Boolean] = None,
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
            )
            val sql =
                fr"SELECT * FROM $Table " ++ where ++
                fr"ORDER BY ${Table.createdAt} ASC"
            sql.query[Review].stream
        }

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
                _ <- courseRepository.updateStats(updCourse.code, updCourse.stats, updCourse.tagStats)
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
                Table.comments --> comments
            )

            val statUpds: List[(Fragment, Fragment)] = stats.toList.map( (stat, value) => Table.stat(stat) --> value)

            Table.insertInto(upds ++ statUpds).update.withUniqueGeneratedKeys[Review](Table.columnNames*)
        }
    }
}
