package cl.cadcc.ramitos.repository

import cats.*
import cats.syntax.all.*, cats.effect.syntax.all.*
import cats.data.NonEmptyVector
import cl.cadcc.ramitos.config.TagSettings
import cl.cadcc.ramitos.model.{Course, CourseStat, Stat}
import doobie.*
import doobie.implicits.*
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

trait CourseRepositoryOps {
    def getByCode(code: String, forUpdate: Boolean = false): ConnectionIO[Option[Course]]
    def list(limit: Long, codes: Seq[String] = Seq.empty, from: Option[String] = None): Stream[ConnectionIO, Course]
    def create(code: String, name: String): ConnectionIO[Course]
    def updateStats(code: String, stats: Map[Stat, CourseStat], tagStats: Map[String, CourseStat]): ConnectionIO[Boolean]
}

type CourseRepository[F[_]] = CourseRepositoryOps & MufasaPoller[F]

object CourseRepository {

    def apply[F[_]](using ev: CourseRepository[F]): CourseRepository[F] = ev

    def of[F[_]: {Transactor, Temporal, Supervisor, MufasaApi}](tagSettings: TagSettings): F[CourseRepository[F]] =
        val ops = CourseRepositoryOpsImpl(tagSettings)
        val cache = MufasaCache(ops)
        given MufasaCacheKeyRepository[CourseCacheKey] = MufasaCacheKeyRepository.make("CourseRepository")
        MufasaPoller.selfScheduling(cache.updateFromMufasa)
            .map { poller =>
                new CourseRepositoryOpsImpl(tagSettings) with MufasaPoller.Mixin(poller)
            }

    private case class CourseCacheKey(
        updatedAt: Instant,
    ) derives Codec

    private class MufasaCache[F[_]: {
        Transactor as xa,
        Temporal as F,
        MufasaApi as mufasa
    }](courseRepo: CourseRepositoryOpsImpl) {
        type CourseData = (String, String)

        private def ramoToCourseData(now: Instant)(ramo: Ramo): Either[Throwable, CourseData] =
            for {
                code <- ramo.codigo.toRight(Exception("Missing course code"))
                name <- ramo.nombre.toRight(Exception("Missing course name"))
            } yield (code, name)

        private def processRamos(now: Instant, ramos: Stream[F, Ramo]): Stream[F, Chunk[CourseData]] =
            ramos
                .filter(_.codigo.fold(false)(_.startsWith("CC")))
                .map(ramoToCourseData(now))
                .evalMapFilter {
                    case Left(value) => None.pure
                    case Right(value) => value.some.pure
                }
                .chunkN(500, true)

        private def persistChunk(chunk: Chunk[(String, String)]): F[Unit] =
            courseRepo.bulkCreate(chunk.toNel.get).transact(xa).void

        private def persistCourses(courses: Stream[F, Chunk[(String, String)]]): Stream[F, Unit] =
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

        private val emptyTagStats =
            tagsList
            .map { tagName =>
                tagName -> CourseStat(Float.NaN, 0, 0L) }
            .toMap

        def getByCode(code: String, forUpdate: Boolean = false): ConnectionIO[Option[Course]] =
            val sql =
                fr"SELECT ${Table.all} FROM $Table WHERE ${Table.code === code}"
                    ++ (if forUpdate then fr"FOR UPDATE" else fr"")

            sql.query[Course]
                .option

        def list(limit: Long, codes: Seq[String] = Seq.empty, from: Option[String] = None): Stream[ConnectionIO, Course] =
            val where = mkWhere(
                from.map(Table.code > _),
                if codes.isEmpty then None
                else Some(Table.code.in(codes)),
            )
            val sql =
                fr"SELECT ${Table.all} FROM $Table"
                ++ where
                ++ fr"ORDER BY ${Table.code} LIMIT $limit"
            sql.query[Course].stream

        def create(code: String, name: String): ConnectionIO[Course] = {
            Table.insertInto(NonEmptyVector.of(
                    Table.code --> code,
                    Table.displayName --> name,
                    Table.tagStats --> emptyTagStats ))
                .update
                .withUniqueGeneratedKeys(Table.columnNames*)
        }

        def updateStats(code: String, stats: Map[Stat, CourseStat], tagStats: Map[String, CourseStat]): ConnectionIO[Boolean] =
            for {
                now <- ConnF.realTimeInstant
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

        private[CourseRepository] def bulkCreate(data: NonEmptyList[(String, String)]): ConnectionIO[Int] =
            val rows = data.map { (l, r) => fr"($l, $r, $emptyTagStats)" }.reduceLeft { (l, r) => fr"$l, $r" }
            sql"""
                INSERT INTO $Table (${Table.code}, ${Table.displayName}, ${Table.tagStats})
                VALUES $rows
                ON CONFLICT (code) DO NOTHING"""
                .update.run
    }
}
