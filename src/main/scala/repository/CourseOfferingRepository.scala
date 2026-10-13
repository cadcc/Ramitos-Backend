package cl.cadcc.ramitos.repository

import cats.*
import cats.effect.*
import cats.syntax.all.*, cats.effect.syntax.all.*
import cl.cadcc.ramitos.model.DBCourseOffering
import cl.cadcc.ramitos.model.Semester
import doobie.ConnectionIO
import fs2.Stream
import mufasa.*
import java.time.Instant
import io.circe.Codec
import cl.cadcc.ramitos.model.Course
import cats.data.OptionT
import cl.cadcc.ramitos.model.SemesterType
import cats.data.EitherT
import fs2.Chunk
import cats.data.NonEmptyList
import cats.data.NonEmptyList
import doobie.syntax.all.*
import doobie.util.transactor.Transactor
import cats.effect.std.Supervisor
import org.typelevel.log4cats.LoggerFactory
import scala.language.implicitConversions

trait DBCourseOfferingRepositoryOps {
  def list(
    course: Option[String] = None,
    year: Option[String] = None,
    semester: Option[Semester] = None,
    section: Option[Short] = None,
  ) : Stream[ConnectionIO, DBCourseOffering]
}

trait CourseOfferingMufasaPatch[F[_]] {
  def getByMufasaId(id: Int): F[Option[DBCourseOffering]]
}

type DBCourseOfferingRepository[F[_]] = DBCourseOfferingRepositoryOps & MufasaPoller[F] & CourseOfferingMufasaPatch[F]

object DBCourseOfferingRepository {
  val Table = DBCourseOffering.Table

  def apply[F[_]](using ev: DBCourseOfferingRepository[F]) = ev

  def ofTemporal[F[_]: {Temporal, Transactor as xa, MufasaApi, Supervisor, LoggerFactory}]: F[DBCourseOfferingRepository[F]] =
    val ops = DBCourseOfferingRepositoryOpsImpl()
    val pollerOps = Poller[F](ops)
    given MufasaCacheKeyRepository[CacheKey] = MufasaCacheKeyRepository.make("CourseOfferingRepository")
    for {
      poller <- MufasaPoller.selfScheduling[F, CacheKey](pollerOps.updateFromMufasa)
      map <- ops.listAll
        .transact(xa)
        .map { offer => offer.mufasaOfferingId -> offer }
        .compile
        .to(Map)
      ans = new DBCourseOfferingRepositoryOpsImpl() with MufasaPoller.Mixin(poller) with CourseOfferingMufasaPatch[F] {
        def getByMufasaId(id: Int): F[Option[DBCourseOffering]] = map.get(id).pure
      }
    } yield ans

  private case class CacheKey(
    semester: Semester,
    updatedAt: Instant,
  ) derives Codec

  private class Poller[F[_]: {
    Temporal as F,
    MufasaApi as mufasa,
    Transactor as xa,
    LoggerFactory as logging,
  }](ops: DBCourseOfferingRepositoryOpsImpl) {
    private val logger = logging.getLogger
    
    opaque type CourseCode = String
    opaque type OfferingData = (Int, Semester, CourseCode, Short)
    opaque type PeriodoId = String

    private val yearRange: Short = 1 // TODO: CHANGE to higher number, left like this to test

    def periodoIdToSemester(periodoId: PeriodoId): F[Semester] =
      OptionT.fromOption(Semester.fromMufasaOption(periodoId))
        .getOrRaise(AssertionError(s"invalid period id '$periodoId' from MUFASA."))

    def periodoToSemester(periodo: Periodo): F[Semester] =
      for {
        periodoId <- OptionT.fromOption(periodo.idPeriodo)
          .getOrRaise(AssertionError("MUFASA returned a periodo without id"))
        semester <- periodoIdToSemester(periodoId)
      } yield semester

    def semesterToPeriodoId(semester: Semester): PeriodoId =
        s"${semester.year}.${semester.semesterType.index}"
        
    def getCurrentSemester: F[Semester] =
      for {
        periodos <- mufasa.listPeriodos(IntFlag.SET.some)
        activo <- OptionT.fromOption(periodos.content.get(0)).getOrRaise(AssertionError("MUFASA returned 0 entries when asking for the active period"))
        semester <- periodoToSemester(activo)
      } yield semester

    def getPeriodRange(currentSemester: Semester, oldKey: Option[CacheKey]): Stream[F, PeriodoId] =
      val startSemester = oldKey.map(_.semester).getOrElse(currentSemester.minusYears(yearRange))
      Semester.between(startSemester, currentSemester).map(semesterToPeriodoId)

    def pullOfferings(periods: Chunk[PeriodoId]): Stream[F, OfferingData] =
      Stream.evalSeq(
          mufasa.listCursos(periods.toVector.some)
          .map(_.content)
          .flatTap { vec =>
            logger.debug(s"Pulled CourseOfferings from MUFASA for semesters ${periods} and got ${vec.length} entries")
          })
        .evalTap {curso => logger.trace(s"Processing Mufasa Offering $curso")}
        .collect {
          case c@Curso(idInstitucion = Some("5")) => c.asRight
          case Curso(idInstitucion = Some(id)) => AssertionError(s"id_institution is $id, not 5 (DCC)").asLeft
          case c@Curso(codigo = Some(v)) if v.startsWith("CC") => c.asRight
          case Curso(codigo = Some(v)) => AssertionError(s"Code '$v' does not start with 'CC'").asLeft
          case _ => AssertionError(s"Offering does not have a code not id_institucion.").asLeft
        }
        .evalMapFilter {
          case Right(v) => v.some.pure
          case Left(err) => logger.debug(err)("Skipped course.") *> None.pure
        }
        .map { curso =>
          for {
            year <- curso.anno
              .flatMap(_.toShortOption)
              .toRight(AssertionError("Missing year for this offering"))
            period <- curso.periodo
              .flatMap(_.toIntOption)
              .flatMap(SemesterType.ofIndex)
              .toRight(AssertionError("Missing periodo for this offering"))
            courseCode <- curso.codigo
              .toRight(AssertionError("Missing codigo for this offering"))
            section <- curso.seccion
              .flatMap(_.toShortOption)
              .toRight(AssertionError("Missing section for this offering"))
            offerId <- curso.idCurso
              .flatMap(_.toIntOption)
              .toRight(AssertionError("Missing cursoId for this offering"))
          } yield (offerId, year, period, courseCode, section)
        }
        .evalMapFilter {
          case Right(v) => v.some.pure
          case Left(err) => logger.trace(err)(s"Could not extract data from course.") *> None.pure
        }
        .map { par => (par._1, Semester(par._2, par._3), par._4, par._5) }

    def persistData(data: Chunk[OfferingData]): F[Unit] =
      ops.bulkCreate(NonEmptyList.fromListUnsafe(data.toList))
        .transact(xa).void

    def streammingUpdate(currentSemester: Semester, oldKey: Option[CacheKey]): Stream[F, Unit] =
      getPeriodRange(currentSemester, oldKey)
        .evalTap(period => logger.debug(s"Updating CourseOfferings for period $period"))
        .chunkN(4) // MUFASA only allows to ask for 4 semesters at a time
        .flatMap(pullOfferings)
        .chunkN(500)
        .evalTap(_ => logger.debug("Persisting CourseOfferings..."))
        .evalMap(persistData)
      
    def updateFromMufasa(oldKey: Option[CacheKey]): F[(CacheKey, Option[Instant])] =
      for {
        _ <- logger.info("Updating CourseOffering cache from MUFASA")
        now <- F.realTimeInstant
        currentSemester <- getCurrentSemester
        _ <- logger.info(s"Updating CourseOffering cache: Current semester is ${currentSemester}")
        _ <- streammingUpdate(currentSemester, oldKey)
          .compile.drain
        _ <- logger.info("Done updating CourseOffering cache.")
      } yield (CacheKey(currentSemester, now), None)
  }

  private class DBCourseOfferingRepositoryOpsImpl extends DBCourseOfferingRepositoryOps {
    def list(
      course: Option[String] = None,
      year: Option[String] = None,
      semester: Option[Semester] = None,
      section: Option[Short] = None,
    ) : fs2.Stream[ConnectionIO, DBCourseOffering] = ???

    private[DBCourseOfferingRepository] def bulkCreate(data: NonEmptyList[(Int, Semester, String, Short)]): ConnectionIO[Int] =
      import scala.language.implicitConversions
      val rows = data
        .map { (id, s, c, i) => fr"($id, $c, ${s.year}, ${s.semesterType}, $i)" }
        .reduceLeft { (l, r) => fr"$l, $r" }
      sql"""
      WITH insert_data (mufasaId, code, year, semester, section) AS (
        VALUES $rows
      )
      INSERT INTO $Table (${Table.mufasaOfferingId}, ${Table.courseCode}, ${Table.year}, ${Table.semesterType}, ${Table.section})
      SELECT data.mufasaId, data.code, data.year, data.semester, data.section
      FROM insert_data as data
      WHERE EXISTS (
        SELECT 1 FROM courses WHERE data.code = courses.code
      )
      ON CONFLICT (${Table.courseCode}, ${Table.year}, ${Table.semester}, ${Table.section}) DO NOTHING
      """
        .update.run

    private[DBCourseOfferingRepository] def listAll: Stream[ConnectionIO, DBCourseOffering] =
      sql"SELECT ${Table.all} FROM $Table"
        .query[DBCourseOffering]
        .stream
  }
}
