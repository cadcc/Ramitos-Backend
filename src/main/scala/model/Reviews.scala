package cl.cadcc.ramitos.model

import doobie._, doobie.postgres.implicits._
import java.time.Instant

case class Review(
    id: Int,
    accountId: Int,
    courseCode: String,
    comments: String,
    difficulty: Option[Int],
    load: Option[Int],
    utility: Option[Int],
    interest: Option[Int],
    createdAt: Instant
)

object Review {
    object Table extends TableDefinition("reviews") {
        val id: Column[Int] = Column("id")
        val accountId: Column[Int] = Column("account_id")
        val courseCode: Column[String] = Column("course_code")
        val comments: Column[String] = Column("comments")
        val difficulty: Column[Option[Int]] = Column("difficulty")
        val load: Column[Option[Int]] = Column("load")
        val utility: Column[Option[Int]] = Column("utility")
        val interest: Column[Option[Int]] = Column("interest")
        val createdAt: Column[Instant] = Column("created_at")

        object all extends WithSQLDefinition[Review](Composite((
            id.sqlDef,
            accountId.sqlDef,
            courseCode.sqlDef,
            comments.sqlDef,
            difficulty.sqlDef,
            load.sqlDef,
            utility.sqlDef,
            interest.sqlDef,
            createdAt.sqlDef
        ))(Review.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[Review](this)
    }
}
