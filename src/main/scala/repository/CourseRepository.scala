package cl.cadcc.ramitos.repository

import cats.*
import cats.syntax.all.*, cats.effect.syntax.all.*
import cats.data.NonEmptyVector
import cl.cadcc.ramitos.config.TagSettings
import cl.cadcc.ramitos.model.{Course, CourseStat, Stat}
import doobie.*
import doobie.implicits.*
import cl.cadcc.ramitos.model.implicits.given
import doobie.syntax.all.*
import fs2.Stream

import java.time.Instant
import scala.language.implicitConversions
import cats.effect.MonadCancel
import cats.effect.Concurrent
import cats.effect.MonadCancelThrow
import cats.effect.Temporal
import mufasa.MufasaApi
import cl.cadcc.ramitos.model.SemesterType
import io.circe.Codec
import java.time.ZoneId
import mufasa.Date
import smithy4s.time.LocalDate
import fs2.Chunk
import cats.data.NonEmptySeq
import cl.cadcc.ramitos.utils.extensions.*
import cats.data.NonEmptyList
import cats.effect.std.Supervisor
import mufasa.Ramo
import cl.cadcc.ramitos.model.implicits.given
import cats.data.OptionT
import org.typelevel.log4cats.LoggerFactory

trait CourseRepositoryOps {
    def getByCode(code: String, forUpdate: Boolean = false): ConnectionIO[Option[Course]]
    def list(limit: Long, codes: Option[Seq[String]] = None, from: Option[String] = None): Stream[ConnectionIO, Course]
    def create(code: String, mufasaId: Int, name: String): ConnectionIO[Course]
    def updateStats(code: String, stats: Map[Stat, CourseStat], tagStats: Map[String, CourseStat], hasComment: Boolean): ConnectionIO[Boolean]
}

trait CourseMufasaPatch[F[_]] {
    def getCode(courseId: Int): F[Option[String]]
}

type CourseRepository[F[_]] = CourseRepositoryOps & MufasaPoller[F] & CourseMufasaPatch[F]

object CourseRepository {

    def apply[F[_]](using ev: CourseRepository[F]): CourseRepository[F] = ev

    def of[F[_]: {Transactor as xa, Temporal, Supervisor, MufasaApi, LoggerFactory as logging}](tagSettings: TagSettings): F[CourseRepository[F]] =
        val logger = logging.getLogger
        val ops = CourseRepositoryOpsImpl(tagSettings)
        val cache = MufasaCache(ops)
        given MufasaCacheKeyRepository[CourseCacheKey] = MufasaCacheKeyRepository.make("CourseRepository")
        for {
            upgradedCourses <- ops.upgradeTags.transact(xa)
            _ <- logger.info(s"Upgraded $upgradedCourses to the new tags")
            poller <- MufasaPoller.selfScheduling(cache.updateFromMufasa)
            allCourses <- ops.listAll
                .transact(xa)
                .map { course => course.mufasaCourseId -> course.code }
                .compile
                .to(Map)
            ans: CourseRepository[F] =
                new CourseRepositoryOpsImpl(tagSettings)
                    with MufasaPoller.Mixin(poller)
                    with CourseMufasaPatch[F]
                {
                    override def getCode(courseId: Int): F[Option[String]] = allCourses.get(courseId).pure 
                }
        } yield ans

    private case class CourseCacheKey(
        updatedAt: Instant,
    ) derives Codec

    private class MufasaCache[F[_]: {
        Transactor as xa,
        Temporal as F,
        MufasaApi as mufasa
    }](courseRepo: CourseRepositoryOpsImpl) {
        type CourseData = (String, Int, String)

        private def ramoToCourseData(now: Instant)(ramo: Ramo): Either[Throwable, CourseData] =
            for {
                code <- ramo.codigo.toRight(Exception("Missing course code"))
                id <- ramo.idRamo.toRight(Exception("Missing course id"))
                    .flatMap(_.toIntOption.toRight(Exception("Course id was not integer")))
                name <- ramo.nombre.toRight(Exception("Missing course name"))
            } yield (code, id, name)

        private def processRamos(now: Instant, ramos: Stream[F, Ramo]): Stream[F, Chunk[CourseData]] =
            ramos
                .filter(_.idInstitucion.fold(false)(_ == "5"))
                .map(ramoToCourseData(now))
                .evalMapFilter {
                    case Left(value) => None.pure
                    case Right(value) => value.some.pure
                }
                .chunkN(500, true)

        private def persistChunk(chunk: Chunk[(String, Int, String)]): F[Unit] =
            courseRepo.bulkCreate(chunk.toNel.get).transact(xa).void

        private def persistCourses(courses: Stream[F, Chunk[(String, Int, String)]]): Stream[F, Unit] =
            courses.evalMap(persistChunk)
        
        def updateFromMufasa(cacheKey: Option[CourseCacheKey]): F[(CourseCacheKey, Option[Instant])] =
            val fromDate = cacheKey
                .map( _.updatedAt
                    .atZone(ZoneId.of("America/Santiago"))
                    .minusDays(1)
                    .toLocalDate
                )
            for {
                now <- F.realTimeInstant
                rawRamos <- mufasa.listRamos(
                    query = "CC".some,
                    desde = fromDate.map { date => Date(LocalDate(date.toEpochDay())) }
                ).map(_.content)
                courses = processRamos(now, Stream.emits(rawRamos).evalMap(_.pure))
                dbOps = persistCourses(courses)
                _ <- dbOps.compile.drain
            } yield (CourseCacheKey(now), None)
    }

    private class CourseRepositoryOpsImpl(
        private val tagSettings: TagSettings
    ) extends CourseRepositoryOps {
        private val ConnF = WeakAsync[ConnectionIO]
        private val Table = Course.Table
        private val tagsList: List[String] = tagSettings.allTags.toList

        private val emptyStat = CourseStat(None, 0, 0L)
        
        private val emptyTagStats =
            tagsList
            .map { tagName =>
                tagName -> emptyStat }
            .toMap

        private val emptyStats: Map[Stat, CourseStat] =
            Map(
                Stat.DOCENCIA -> emptyStat,
                Stat.VIBES -> emptyStat,
                Stat.RELEVANCIA -> emptyStat,
                Stat.CARGA -> emptyStat,
                Stat.CARGA -> emptyStat,
                Stat.DIFICULTAD -> emptyStat,
            )

        def getByCode(code: String, forUpdate: Boolean = false): ConnectionIO[Option[Course]] =
            val sql =
                fr"SELECT ${Table.all} FROM $Table WHERE ${Table.code === code}"
                    ++ (if forUpdate then fr"FOR UPDATE" else fr"")

            sql.query[Course]
                .option

        def list(limit: Long, codes: Option[Seq[String]] = None, from: Option[String] = None): Stream[ConnectionIO, Course] =
            val where = mkWhere(
                from.map(Table.code > _),
                codes.map(c => Table.code.in(c))
            )
            val sql =
                sql"""
                    SELECT ${Table.all} FROM $Table
                    $where
                    ORDER BY ${Table.code}
                    LIMIT $limit
                """
            sql.query[Course].stream

        def create(code: String, mufasaId: Int, name: String): ConnectionIO[Course] = {
            Table.insertInto(NonEmptyVector.of(
                    Table.code --> code,
                    Table.mufasaCourseId --> mufasaId,
                    Table.displayName --> name,
                    Table.totalReviews --> 0,
                    Table.commentReviews --> 0,
                    Table.stats --> emptyStats,
                    Table.tagStats --> emptyTagStats ))
                .update
                .withUniqueGeneratedKeys(Table.columnNames*)
        }

        def updateStats(code: String, stats: Map[Stat, CourseStat], tagStats: Map[String, CourseStat], hasComment: Boolean): ConnectionIO[Boolean] =
            for {
                now <- ConnF.realTimeInstant
                sql =
                    sql"${
                        Table.updateTable(
                            Table.updatedAt --> now,
                            Table.stats --> stats,
                            Table.tagStats --> tagStats,
                            Table.totalReviews --> (Table.totalReviews + 1),
                            Table.commentReviews --> (if hasComment then Table.commentReviews + 1 else Table.commentReviews)
                        )
                    } WHERE ${Table.code === code}"
                ans <- sql.update.run.map(_ == 1)
            } yield ans

        private[CourseRepository] def bulkCreate(data: NonEmptyList[(String, Int, String)]): ConnectionIO[Int] =
            val rows = data.map { (c, id, n) => fr"($c, $id, $n, $emptyStats, $emptyTagStats, 0, 0)" }.reduceLeft { (l, r) => fr"$l, $r" }
            sql"""
                INSERT INTO $Table (${Table.code}, ${Table.mufasaCourseId}, ${Table.displayName}, ${Table.stats}, ${Table.tagStats}, ${Table.totalReviews}, ${Table.commentReviews})
                VALUES $rows
                ON CONFLICT (code) DO NOTHING
            """
                .update.run

        private[CourseRepository] def listAll: Stream[ConnectionIO, Course] =
            sql"SELECT ${Table.all} FROM $Table"
                .query[Course].stream

        private[CourseRepository] def upgradeTags: ConnectionIO[Int] =
            sql"""
                UPDATE $Table SET
                ${Table.tagStats} = $emptyTagStats || ${Table.tagStats}
            """
                .update
                .run
    }
}
