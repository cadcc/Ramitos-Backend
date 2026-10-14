package cl.cadcc.ramitos.routes

import cats.*
import cats.syntax.all.*
import cats.effect.*
import fs2.Stream
import cats.effect.syntax.all.*
import doobie.*
import doobie.syntax.all.*
import cl.cadcc.ramitos.schema.*
import cl.cadcc.ramitos.model.{Stat, Course as ModelCourse, CourseStat as ModelCourseStat}
import cl.cadcc.ramitos.utils.extensions.*
import doobie.util.transactor.Transactor
import cl.cadcc.ramitos.repository.CourseRepository
import cats.data.OptionT
import smithy4s.kinds.PolyFunction5
import smithy4s.time.Timestamp
import cats.mtl.Ask
import cl.cadcc.ramitos.middleware.AuthMiddleware.Session
import cl.cadcc.ramitos.repository.CourseEnrollmentRepository
import cl.cadcc.ramitos.model.Account

class CourseImpl[
    F[_] : {
        MonadCancelThrow as F,
        Transactor as xa,
        CourseRepository as courseRepository,
        CourseEnrollmentRepository as enrollRepository,
        ([F[_]] =>> Ask[F, Option[Session]]) as session,
    }
] extends CourseService[F] {

    private def statToSchema(stat: ModelCourseStat): Option[CourseStat] =
        stat.rate.map(CourseStat.apply)

    /** HTTP GET /api/courses */
    override def listCourses(limit: Long, codes: Option[List[String]], enrolled: Option[Boolean], reviewable: Option[Boolean], after: Option[String]): F[ListCoursesOutput] =
        val enroll = enrolled.getOrElse(false)
        val review = reviewable.getOrElse(false)
        session.ask.flatMap {
            case Some(Session(account = Account(mufasaId = Some(v)))) => listAuth(v, limit, codes, enroll, review, after)
            case _ => listNoAuth(limit, codes, after)
        }

    private def listAuth(mufasaId: String, limit: Long, codes: Option[List[String]], enrolled: Boolean, reviewable: Boolean, after: Option[String]): F[ListCoursesOutput] =
        for {
            enrolledCourses <-
                enrollRepository
                    .getEnrollments(mufasaId, firstBySemester = true, orderAscending = false)
                    .transact(xa)
            enrolledCodes = enrolledCourses.map(_.courseCode).toSet
            reqCodes = codes match {
                case Some(l) => l.toSet
                case None => enrolledCodes
            }
            listCodes =
                if enrolled then enrolledCodes.intersect(reqCodes).toSeq
                else codes.getOrElse(List.empty).toSeq
            courses <- courseRepository
                .list(limit, listCodes, after)
                .map(course => modelToSchema(enrolledCodes.contains(course.code).some)(course))
                .compile
                .toList
                .transact(xa)
        } yield ListCoursesOutput(courses)

    private def listNoAuth(limit: Long, codes: Option[List[String]], after: Option[String]): F[ListCoursesOutput] =
        courseRepository.list(limit, codes.getOrElse(List.empty), after)
            .map(modelToSchema(None))
            .compile.toList
            .transact(xa)
            .map(ListCoursesOutput.apply)

    private def modelToSchema(enrolled: Option[Boolean])(course: ModelCourse): Course =
        Course(
            id = course.code,
            name = course.name,
            reviewCount = 0,
            stats = CourseStats(
                docencia = statToSchema(course.stats(Stat.DOCENCIA)),
                vibes = statToSchema(course.stats(Stat.VIBES)),
                relevancia = statToSchema(course.stats(Stat.RELEVANCIA)),
                carga = statToSchema(course.stats(Stat.CARGA)),
                dificultad = statToSchema(course.stats(Stat.DIFICULTAD)),
            ),
            tag_stats = course.tagStats.mapFilter(_.rate.map(CourseStat.apply)),
            enrolled = enrolled,
            reviewable = enrolled,
        )

    def getCourse(courseId: String): F[Course] =
        for {
            course <- OptionT(courseRepository.getByCode(courseId).transact(xa))
                .getOrRaise(new NotFound())
            enrolled <- session.ask.flatMap {
                case Some(Session(account = Account(mufasaId = Some(mufasaId)))) => isEnrolled(courseId, mufasaId).map(Some.apply)
                case Some(_) => false.some.pure
                case None => None.pure
            }
        } yield modelToSchema(enrolled)(course)

    def isEnrolled(courseId: String, mufasaId: String): F[Boolean] =
        enrollRepository.getEnrollmentsFor(mufasaId, courseId)
            .transact(xa)
            .map(vec => !vec.isEmpty)

    /** HTTP GET /api/courses.json */
    override def getCoursesStaticData(): F[GetCoursesStaticDataOutput] =
        F.raiseError(Exception("getCoursesStaticData OPERATION EXPOSED!! THIS SHOULD BE AFTER STREAMING IMPLEMENTATION"))
}
