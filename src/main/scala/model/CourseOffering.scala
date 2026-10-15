package cl.cadcc.ramitos.model

import cats.syntax.all.*
import doobie.{Meta, TableDefinition, WithSQLDefinition}
import doobie.postgres.implicits.pgEnumStringOpt
import doobie.util.{Read, Write}
import io.circe.Codec
import doobie.Column
import doobie.Composite
import java.time.Instant
import java.util.Date
import doobie.SQLDefinition
import implicits.given

sealed trait CourseOffering(
    _id: Option[Int],
    val courseCode: String,
    val mufasaOfferingId: Int,
    val semester: Semester,
    val section: Short,
) {
    def id: Option[Int] = _id

    def toTransient: TransientCourseOffering =
        TransientCourseOffering(this.courseCode, this.mufasaOfferingId, this.semester, this.section)
}

case class DBCourseOffering(
    realId: Int,
    private val _courseCode: String,
    private val _mufasaOfferingId: Int,
    private val _semester: Semester,
    private val _section: Short,
) extends CourseOffering(realId.some, _courseCode, _mufasaOfferingId, _semester, _section)
  derives Codec, Read, Write {
    override val id : Some[Int] = Some(realId)
}

object DBCourseOffering {
    object Table extends TableDefinition("course_offerings") {
        lazy val columns = Composite(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector
        
        val id: Column[Int] = Column("id")
        val courseCode: Column[String] = Column("course_code")
        val mufasaOfferingId: Column[Int] = Column("mufasa_offering_id")
        val year: Column[Short] = Column("year")
        val semesterType: Column[SemesterType] = Column("semester")
        val section: Column[Short] = Column("section")

        val semester: SQLDefinition[Semester] = Composite((
            year.sqlDef,
            semesterType.sqlDef
        ))(Semester.apply)(Tuple.fromProductTyped)

        object all extends WithSQLDefinition[DBCourseOffering](Composite((
            id.sqlDef,
            courseCode.sqlDef,
            mufasaOfferingId.sqlDef,
            semester.sqlDef,
            section.sqlDef
        ))(DBCourseOffering.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[DBCourseOffering](this)
    }
}

case class TransientCourseOffering(
    private val _courseCode: String,
    private val _mufasaOfferingId: Int,
    private val _semester: Semester,
    private val _section: Short,
) extends CourseOffering(None, _courseCode, _mufasaOfferingId, _semester, _section)
  derives Codec, Read, Write {
    override val id: None.type = None
}
