package cl.cadcc.ramitos.repository

import cats.syntax.all.*
import cats.effect.*, cats.effect.syntax.all.*
import doobie.*, doobie.syntax.all.*
import cl.cadcc.ramitos.model.implicits.given
import cl.cadcc.ramitos.model.CourseEnrollment
import cl.cadcc.ramitos.model.Semester
import cl.cadcc.ramitos.model.CourseOffering
import cl.cadcc.ramitos.model.CourseEnrollmentCache
import cl.cadcc.ramitos.model.TransientCourseOffering
import cl.cadcc.ramitos.model.DBCourseOffering
import cl.cadcc.ramitos.model.implicits.given
import scala.language.implicitConversions
import mufasa.MufasaApi
import org.typelevel.log4cats.LoggerFactory
import fs2.Stream
import java.time.Instant
import fs2.Pipe
import fs2.Chunk
import cl.cadcc.ramitos.model.CourseEnrollmentCache.Table.semesterSynced
import cl.cadcc.ramitos.repository.DBCourseOfferingRepository
import java.time.temporal.TemporalUnit
import java.time.temporal.ChronoUnit
import cats.data.OptionT
import cats.data.EitherT

/// Any enrollment from a semester before the
/// watermark will not change in our database.
/// Any enrollment at or after the watermark
/// could be deleted or added at any time.
trait CourseEnrollmentRepository[F[_]] {
  /// Hits the cache that the offerings for this mufasa cache
  /// may be used soon. If the cache is stale, updates it, if
  /// not does nothing.
  def poll(mufasaId: String): F[Unit]

  /// Invalidates the cache, forcing a reload on the next
  /// poll()
  def invalidate(mufasaId: String): F[Unit]
  
  /// This may contain uncommitted enrollments, joins may not work.
  def getEnrollments(mufasaId: String, firstBySemester: Boolean = false, orderAscending: Boolean = true): ConnectionIO[Vector[CourseOffering]]

  /// Get enrollments that will no longer change
  def getLiveEnrollments(mufasaId: String, firstBySemester: Boolean = false, orderAscending: Boolean = true): ConnectionIO[Vector[TransientCourseOffering]]

  /// get enrollments that may change
  def getSealedEnrollments(mufasaId: String, firstBySemester: Boolean = false, orderAscending: Boolean = true): ConnectionIO[Vector[DBCourseOffering]]

  def getEnrollmentsFor(mufasaId: String, courseCode: String): ConnectionIO[Vector[CourseOffering]]

  def getWatermark: F[Semester]
  def advanceWatermark(watermark: Semester): F[Unit]
}

object CourseEnrollmentRepository {

  def apply[F[_]](using ev: CourseEnrollmentRepository[F]) = ev

  def of[F[_]: {Temporal, MufasaApi, Transactor, LoggerFactory, SemesterRepository as semRepo, DBCourseOfferingRepository}]: F[CourseEnrollmentRepository[F]] =
    for {
      currSemester <- semRepo.getCurrentSemester
      ref <- Ref.of[F, Semester](currSemester.prev)
    } yield CourseEnrollmentRepositoryImpl[F](ref)
  
  private class CourseEnrollmentRepositoryImpl[
    F[_]: {
      Temporal as F,
      MufasaApi as mufasa,
      Transactor as xa,
      LoggerFactory as logging,
      SemesterRepository as semesterRepo,
      DBCourseOfferingRepository as offeringRepo,
    }
  ](
    watermarkRef: Ref[F, Semester]
  ) extends CourseEnrollmentRepository[F] {
    private val logger = logging.getLogger

    private val Table = CourseEnrollment.Table
    private val CacheTable = CourseEnrollmentCache.Table
    private val OfferingTable = DBCourseOffering.Table
    
    override def poll(mufasaId: String): F[Unit] =
      for {
        now <- F.realTimeInstant
        semesterFloor <- semesterRepo.getSemesterFloor
        semesterCeil <- semesterRepo.getSemesterCeil
        cache <- getCacheOrCreate(now, mufasaId, semesterFloor).transact(xa)
        watermark <- getWatermark
        _ <-
          if cache.lastUpdate.isBefore(now.plus(6, ChronoUnit.DAYS)) then
            update(mufasaId, cache.semesterSynced, semesterCeil, watermark)
          else F.unit
      } yield ()

    override def invalidate(mufasaId: String): F[Unit] = ???

    override def getEnrollmentsFor(mufasaId: String, courseCode: String): ConnectionIO[Vector[CourseOffering]] =
      val offers = OfferingTable as "offers"
      val enrolls = Table as "enrolls"
      val cache = CacheTable as "cache"
      for {
        seal <- sql"""
            SELECT ${offers(_.all)} FROM $offers JOIN $enrolls ON ${offers(_.id) === enrolls(_.courseOfferingId)}
            WHERE ${enrolls(_.studentMufasaId) === mufasaId}
              AND ${offers(_.courseCode) === courseCode}
          """
          .query[DBCourseOffering]
          .to[Vector]
        live <- getLiveEnrollments(mufasaId)
          .map { _.filter(_.courseCode == courseCode) }
      } yield (seal ++ live)

    override def getEnrollments(mufasaId: String, firstBySemester: Boolean = false, orderAscending: Boolean = true): ConnectionIO[Vector[CourseOffering]] =
      for {
        seal <- getSealedEnrollments(mufasaId, firstBySemester, orderAscending)
        live <- getLiveEnrollments(mufasaId, firstBySemester, orderAscending)
        ans1 = if orderAscending then seal ++ live else live ++ seal
        ans2 = if firstBySemester then ans1.distinctBy(_.courseCode) else ans1
      } yield ans2

    override def getLiveEnrollments(mufasaId: String, firstBySemester: Boolean = false, orderAscending: Boolean = true): ConnectionIO[Vector[TransientCourseOffering]] =
      val sql = sql"""
        SELECT ${CacheTable.transientData} FROM $CacheTable
        WHERE ${CacheTable.mufasaId === mufasaId}  
      """
      sql.query[Vector[TransientCourseOffering]]
        .unique
        .map { vec =>
          if orderAscending then vec.sortWith( _.semester < _.semester )
          else vec.sortWith( _.semester > _.semester ) }
        .map { vec =>
          if firstBySemester then vec.distinctBy(_.courseCode)
          else vec
        }
    
    override def getSealedEnrollments(mufasaId: String, firstBySemester: Boolean = false, orderAscending: Boolean = true): ConnectionIO[Vector[DBCourseOffering]] =
      val orderCols =
        if firstBySemester then fr"${OfferingTable.courseCode}, ${OfferingTable.semester}"
        else fr"${OfferingTable.semester}"
      val order = if orderAscending then fr"ASC" else fr"DESC"
      val cols =
        if firstBySemester then
          fr"DISTINCT ON ({$OfferingTable.courseCode}) ${OfferingTable.id}, ${OfferingTable.mufasaOfferingId}, ${OfferingTable.semester}, ${OfferingTable.section}"
        else fr"{$OfferingTable.courseCode}, ${OfferingTable.id}, ${OfferingTable.mufasaOfferingId}, ${OfferingTable.semester}, ${OfferingTable.section}"
      val sql = sql"""
        SELECT $cols
        FROM $Table JOIN $OfferingTable ON ${Table.courseOfferingId === OfferingTable.id}
        ORDER BY $orderCols $order
      """
      sql.query[(String, Int, Int, Semester, Short)]
        .map { (code, id, mufasaId, semester, section) => (id, code, mufasaId, semester, section) }
        .map(DBCourseOffering.apply)
        .to[Vector]

    override def getWatermark: F[Semester] =
      watermarkRef.get

    override def advanceWatermark(watermark: Semester): F[Unit] =
      watermarkRef.set(watermark)

    private def getCacheOrCreate(now: Instant, mufasaId: String, semesterFloor: Semester): ConnectionIO[CourseEnrollmentCache] =
      sql"""
        INSERT INTO $CacheTable (${CacheTable.mufasaId}, ${CacheTable.transientData}, ${CacheTable.semesterSynced}, ${CacheTable.lastPull})
        VALUES ($mufasaId, ${Vector.empty[TransientCourseOffering]}, ${semesterFloor.prev}, $now)
        ON CONFLICT (${CacheTable.mufasaId}) DO SELECT
        RETURNING *
      """
        .update
        .withUniqueGeneratedKeys(CacheTable.columnNames*)

    private def bulkCreate(mufasaId: String)(data: Chunk[CourseOffering]): ConnectionIO[Unit] =
      val rows = data
        .map { (offer) =>
          fr"(${offer.courseCode}, ${offer.semester.year}, ${offer.semester.semesterType}, ${offer.section})"
        }
        .reduceLeftOption { (l, r) => fr"$l, $r"}
        .get
      sql"""
        WITH insert_data (code, year, semesterType, section) AS (
          VALUES $rows
        )
        INSERT INTO $Table (${Table.studentMufasaId}, ${Table.courseOfferingId})
        SELECT $mufasaId, offerings.${OfferingTable.id}
        FROM insert_data as data JOIN $OfferingTable as offerings ON
          data.code = offerings.course_code AND
          data.year = offerings.year AND
          data.semesterType = offerings.semester AND
          data.section = offerings.section
      """
        .update
        .run
        .void

    private def updateCache(now: Instant, watermark: Semester, mufasaId: String, live: Vector[CourseOffering]): ConnectionIO[Unit] =
      sql"""
        ${CacheTable.updateTable(
            CacheTable.transientData --> live.map(_.toTransient),
            CacheTable.semesterSyncedYear --> watermark.year,
            CacheTable.semesterSyncedType --> watermark.semesterType,
            CacheTable.lastPull --> now,
        )} WHERE ${CacheTable.mufasaId === mufasaId}
      """
        .update
        .run
        .void

    private def getEnrollments(mufasaId: String)(semesters: Chunk[Semester]): Stream[F, DBCourseOffering] =
      Stream.evalSeq(
          mufasa
            .listCursosInscritos(ruts = Vector(mufasaId).some, idsPeriodo = semesters.map(_.toMufasa).toVector.some)
            .map(_.content.toSeq))
          .evalMap { curso =>
            (for {
              offeringId <- EitherT.fromOption(curso.idCurso, "Missing id-curso from MUFASA")
              offering <- OptionT(offeringRepo.getByMufasaId(offeringId)).toRight("Skipped, this enrollment is for an offering not yet cached.")
            } yield offering).value
          }
          .evalTap {
            case Right(v) => F.unit
            case Left(err) => logger.debug(err)
          }
          .collect {
            case Right(v) => v
          }

    private def getMufasaData(mufasaId: String, from: Semester, to: Semester): Stream[F, DBCourseOffering] =
      Semester.between(from, to)
        .chunkN(3)
        .flatMap(getEnrollments(mufasaId))

    private def persistSealed(mufasaId: String)(seal: Chunk[CourseOffering]): F[Unit] =
      bulkCreate(mufasaId)(seal).transact(xa)
    
    private def persistLive(now: Instant, watermark: Semester, mufasaId: String)(live: Vector[CourseOffering]): F[Unit] =
      updateCache(now, watermark, mufasaId, live).transact(xa)

    private def update(mufasaId: String, from: Semester, to: Semester, watermark: Semester): F[Unit] =
      for {
        now <- F.realTimeInstant
        _ <- getMufasaData(mufasaId, from, watermark)
          .chunkN(500)
          .evalMap(persistSealed(mufasaId))
          .compile
          .drain
        _ <- getMufasaData(mufasaId, watermark.next, to)
          .chunkAll
          .map(_.toVector)
          .evalMap(persistLive(now, watermark, mufasaId))
          .compile
          .drain
      } yield ()
  }

  private class CacheImpl[
    F[_]: {
      Temporal as F,
      LoggerFactory as logging,
      Transactor as xa,
      MufasaApi as mufasa,
      CourseEnrollmentRepository as repo,
    }
  ](
    mufasaId: String,
    watermak: Semester,
  ) {
    
  }
}
