package cl.cadcc.ramitos.repository

import cats.*
import cats.effect.*
import cats.syntax.all.*, cats.effect.syntax.all.*
import cl.cadcc.ramitos.model.CourseOffering
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

trait CourseOfferingRepositoryOps {
  def list(
    course: Option[String] = None,
    year: Option[String] = None,
    semester: Option[Semester] = None,
    section: Option[Short] = None,
  ) : Stream[ConnectionIO, CourseOffering]
}

type CourseOfferingRepository[F[_]] = CourseOfferingRepositoryOps & MufasaPoller[F]

object CourseOfferingRepository {
  val Table = CourseOffering.Table

  def apply[F[_]](using ev: CourseOfferingRepository[F]) = ev

  def ofTemporal[F[_]: {Temporal, Transactor, MufasaApi, Supervisor, LoggerFactory}]: F[CourseOfferingRepository[F]] =
    val ops = CourseOfferingRepositoryOpsImpl()
    val pollerOps = Poller[F](ops)
    given MufasaCacheKeyRepository[CacheKey] = MufasaCacheKeyRepository.make("CourseOfferingRepository")
    MufasaPoller.selfScheduling[F, CacheKey](pollerOps.updateFromMufasa).map { poller =>
      new CourseOfferingRepositoryOpsImpl() with MufasaPoller.Mixin(poller)
    }

  private case class CacheKey(
    semester: Semester,
    updatedAt: Instant,
  ) derives Codec

  private class Poller[F[_]: {
    Temporal as F,
    MufasaApi as mufasa,
    Transactor as xa,
    LoggerFactory as logging,
  }](ops: CourseOfferingRepositoryOpsImpl) {
    private val logger = logging.getLogger
    
    opaque type CourseCode = String
    opaque type OfferingData = (Semester, CourseCode, Short)
    opaque type PeriodoId = String

    private val yearRange: Short = 1 // TODO: CHANGE to higher number, left like this to test

    def periodoIdToSemester(periodoId: PeriodoId): F[Semester] =
      for {
        dotPos <- periodoId.indexOf(".").pure.ensure(AssertionError(""))(_ != -1)
        yearOpt = periodoId.substring(0, dotPos).strip.toShortOption
        year <- OptionT.fromOption(yearOpt)
          .getOrRaise(AssertionError(s"Invalid period id '$periodoId' from MUFASA, could not find year."))
        typeIndexOpt = periodoId.substring(dotPos+1)
          .strip
          .toIntOption
          .flatMap(SemesterType.ofIndex)
        semesterType <- OptionT.fromOption(typeIndexOpt)
          .getOrRaise(AssertionError(s"Invalid period id '$periodoId' from MUFASA, could not find period type"))
      } yield Semester(year, semesterType)

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

    def processCursos(cursos: Stream[F, Curso]): Stream[F, OfferingData] =
      cursos
        .collect {
          case c@Curso(codigo = Some(v)) if v.startsWith("CC") => c
        }
        .map { curso =>
          for {
            periodoId <- curso.periodo.toRight(AssertionError("Missing periodo for this offering"))
            courseCode <- curso.codigo.toRight(AssertionError("Missing codigo for this offering"))
            section <- curso.seccion.flatMap(_.toShortOption).toRight(AssertionError("Missing secction for this offering"))
          } yield (periodoId, courseCode, section)
        }
        .collect {
          case Right(v) => v
        }
        .evalMapFilter { par =>
          periodoIdToSemester(par._1).attempt.map {
            case Right(v) => (v, par._2, par._3).some
            case Left(err) => None
          }
        }

    def pullOfferings(periods: Chunk[PeriodoId]): Stream[F, OfferingData] =
      processCursos(
        Stream.evalSeq(mufasa.listCursos(periods.toVector.some)
          .map(_.content)
          .flatTap(vec => logger.debug(s"Pulled CourseOfferings from MUFASA for semesters ${periods} and got ${vec.length} entries"))))

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

  private class CourseOfferingRepositoryOpsImpl extends CourseOfferingRepositoryOps {
    def list(
      course: Option[String] = None,
      year: Option[String] = None,
      semester: Option[Semester] = None,
      section: Option[Short] = None,
    ) : fs2.Stream[ConnectionIO, CourseOffering] = ???

    private[CourseOfferingRepository] def bulkCreate(data: NonEmptyList[(Semester, String, Short)]): ConnectionIO[Int] =
      import scala.language.implicitConversions
      val rows = data.map { (s, c, i) => fr"($c, $s.year, $s.semester, $i)" }.reduceLeft { (l, r) => fr"$l, $r" }
      sql"""
      WITH insert_data (code, year, semester, section) AS (
        VALUES $rows
      )
      INSERT INTO $Table (${Table.courseCode}, ${Table.year}, ${Table.semester}, ${Table.section})
      SELECT data.code, data.year, data.semester, data.section
      FROM insert_data as data
      WHERE EXISTS (
        SELECT 1 FROM courses WHERE data.code = courses.code
      )
      ON CONFLICT (${Table.courseCode}, ${Table.year}, ${Table.semester}, ${Table.section}) DO NOTHING
      """"
        .update.run
  }
}
