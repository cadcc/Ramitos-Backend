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

enum SemesterType(val s: String) derives Codec {
    case YEAR extends SemesterType("year")
    case FALL extends SemesterType("fall")
    case SPRING extends SemesterType("spring")
    case SUMMER extends SemesterType("summer")
}

object SemesterType {
    given Meta[SemesterType] = pgEnumStringOpt[SemesterType]("semester", ofString, _.s)

    def ofString(s: String): Option[SemesterType] = s match {
        case "year" => SemesterType.YEAR.some
        case "fall" => SemesterType.FALL.some
        case "spring" => SemesterType.SPRING.some
        case _ => None
    }
}

case class Semester(year: Short, semesterType: SemesterType) derives Codec, Read, Write

case class CourseOffering(
    id: Long,
    courseCode: String,
    semester: Semester,
    section: Short,
) derives Codec, Read, Write

object CourseOffering {
    object Table extends TableDefinition("course_offerings") {
        lazy val columns = Composite(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector
        
        val id: Column[Long] = Column("id")
        val courseCode: Column[String] = Column("course_code")
        val year: Column[Short] = Column("year")
        val semesterType: Column[SemesterType] = Column("semester")
        val section: Column[Short] = Column("section")

        val semester: SQLDefinition[Semester] = Composite((
            year.sqlDef,
            semesterType.sqlDef
        ))(Semester.apply)(Tuple.fromProductTyped)

        object all extends WithSQLDefinition[CourseOffering](Composite((
            id.sqlDef,
            courseCode.sqlDef,
            semester.sqlDef,
            section.sqlDef
        ))(CourseOffering.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[CourseOffering](this)
    }
}
