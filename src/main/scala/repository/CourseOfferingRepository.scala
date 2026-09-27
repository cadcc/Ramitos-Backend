package cl.cadcc.ramitos.repository

import cl.cadcc.ramitos.model.CourseOffering
import cl.cadcc.ramitos.model.Semester
import doobie.ConnectionIO
import fs2.Stream

trait CourseOfferingRepository {
  def list(
    course: Option[String] = None,
    year: Option[String] = None,
    semester: Option[Semester] = None,
    section: Option[Short] = None,
  ) : Stream[ConnectionIO, CourseOffering]
}

object CourseOfferingRepository {
  val Table = CourseOffering.Table

  def listOfferings(
    course: Option[String] = None,
    year: Option[String] = None,
    semester: Option[Semester] = None,
    section: Option[Short] = None,
  ) : fs2.Stream[ConnectionIO, CourseOffering] = ???

}
