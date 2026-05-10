package cl.cadcc.ramitos.routes

import cats._, cats.syntax.all._
import cats.effect._, cats.effect.syntax.all._
import doobie._, doobie.syntax.all._
import cl.cadcc.ramitos.schema._
import cl.cadcc.ramitos.model.{Course => ModelCourse}
import doobie.util.transactor.Transactor
import cl.cadcc.ramitos.repository.CourseRepository
import cats.data.OptionT

class CourseImpl[F[_] : MonadCancelThrow](using xa: Transactor[F], courseRepository: CourseRepository) extends CourseService[F] {
    private def modelToSchema(course: ModelCourse): Course =
        Course(
            id = course.code,
            name = course.name,
            difficulty = course.difficulty.rate,
            load = course.load.rate,
            utility = course.utility.rate,
            interest = course.interest.rate
        )

    def getCourse(courseId: String): F[Course] =
        OptionT(courseRepository.getByCode(courseId).transact(xa))
            .getOrRaise(new NotFound())
            .map(modelToSchema)

    def listCourses(limit: Long = 50, after: Option[String]): F[ListCoursesOutput] =
        courseRepository.list(limit, after)
            .map(modelToSchema)
            .compile
            .toList
            .transact(xa)
            .map(ListCoursesOutput.apply)
}
