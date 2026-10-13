package cl.cadcc.ramitos.repository

import cats.syntax.all.*
import cats.effect.*, cats.effect.syntax.all.*
import cl.cadcc.ramitos.model.Semester
import cl.cadcc.ramitos.model.SemesterType

trait SemesterRepository[F[_]] {
  def getCurrentSemester: F[Semester]
  def getSemesterFloor: F[Semester]
  def getSemesterCeil: F[Semester]
}

object SemesterRepository {

  def apply[F[_]](using ev: SemesterRepository[F]) = ev

  def of[F[_]: MonadCancelThrow]: SemesterRepository[F] = SemesterRepositoryImpl[F]

  private class SemesterRepositoryImpl[
    F[_]: {
      MonadCancelThrow as F,
    }
  ] extends SemesterRepository[F] {
    def getCurrentSemester: F[Semester] = Semester(year = 2026, semesterType = SemesterType.SPRING).pure

    def getSemesterFloor: F[Semester] = getCurrentSemester.map(_.minusYears(1))
    def getSemesterCeil: F[Semester] = getCurrentSemester
  }
}
