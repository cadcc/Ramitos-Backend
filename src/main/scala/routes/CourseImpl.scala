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
import doobie.util.transactor.Transactor
import cl.cadcc.ramitos.repository.CourseRepository
import cats.data.OptionT
import smithy4s.kinds.PolyFunction5
import smithy4s.time.Timestamp

class CourseImpl[F[_] : MonadCancelThrow as F](using xa: Transactor[F], courseRepository: CourseRepository) extends CourseService[F] {

    private def statToSchema(stat: ModelCourseStat): Option[CourseStat] =
        if stat.rate.isNaN then None
        else CourseStat(stat.rate).some

    /** HTTP GET /api/courses */
    override def listCourses(limit: Long, codes: Option[List[String]], after: Option[String]): F[ListCoursesOutput] = ???

    private def modelToSchema(course: ModelCourse): Course =
        Course(
            id = course.code,
            name = course.name,
            stats = CourseStats(
                docencia = statToSchema(course.stats(Stat.DOCENCIA)),
                vibes = statToSchema(course.stats(Stat.VIBES)),
                relevancia = statToSchema(course.stats(Stat.RELEVANCIA)),
                carga = statToSchema(course.stats(Stat.CARGA)),
                dificultad = statToSchema(course.stats(Stat.DIFICULTAD)),
            ),
            tag_stats = course.tagStats.map { (k, v) => (k, CourseStat(v.rate)) },
        )

    def getCourse(courseId: String): F[Course] =
        OptionT(courseRepository.getByCode(courseId).transact(xa))
            .getOrRaise(new NotFound())
            .map(modelToSchema)

    /** HTTP GET /api/courses.json */
    override def getCoursesStaticData(): F[GetCoursesStaticDataOutput] =
        F.raiseError(Exception("getCoursesStaticData OPERATION EXPOSED!! THIS SHOULD BE AFTER STREAMING IMPLEMENTATION"))
}
