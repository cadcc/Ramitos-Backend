package cl.cadcc.ramitos.model

import doobie.WithSQLDefinition
import doobie.Column
import doobie.Composite
import doobie.TableDefinition
import doobie.postgres.implicits.*
import cl.cadcc.ramitos.model.implicits.given
import java.time.Instant
import doobie.util.Read
import doobie.util.Write
import io.circe.Codec
import doobie.util.Put
import doobie.util.Get
import doobie.SQLDefinition
import implicits.given

case class CourseEnrollment(
  studentMufasaId: String,
  courseOfferingId: Int,
) derives Codec, Read, Write

object CourseEnrollment {
  object Table extends TableDefinition("course_enrollments") {

    val studentMufasaId: Column[String] = Column("student_mufasa_id")
    val courseOfferingId: Column[Int] = Column("course_offering_id")

    object all extends WithSQLDefinition[CourseEnrollment](Composite((
      studentMufasaId.sqlDef,
      courseOfferingId.sqlDef,
    ))(CourseEnrollment.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[CourseEnrollment](this)
  }
}

case class CourseEnrollmentCache(
    mufasaId: String,
    /// These are not DB-Backed!!!, do not use for joins.
    transientData: Vector[TransientCourseOffering],
    /// Permanent data is cached up to this semester
    semesterSynced: Semester,
    lastUpdate: Instant,
) derives Codec, Read, Write

object CourseEnrollmentCache {
    object Table extends TableDefinition("course_enrollment_cache") {
        lazy val columns = Composite(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

        val mufasaId: Column[String] = Column("student_mufasa_id")
        val transientData: Column[Vector[TransientCourseOffering]] = Column("transient_data")
        val semesterSyncedYear: Column[Short] = Column("semester_synced_year")
        val semesterSyncedType: Column[SemesterType] = Column("semester_synced_type")
        val lastPull: Column[Instant] = Column("last_update")

        val semesterSynced: SQLDefinition[Semester] = Composite((
          semesterSyncedYear.sqlDef,
          semesterSyncedType.sqlDef
        ))(Semester.apply)(Tuple.fromProductTyped)

        object all extends WithSQLDefinition[CourseEnrollmentCache](Composite((
            mufasaId.sqlDef,
            transientData.sqlDef,
            semesterSynced,
            lastPull.sqlDef,
        ))(CourseEnrollmentCache.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[CourseEnrollmentCache](this)
    }
}
