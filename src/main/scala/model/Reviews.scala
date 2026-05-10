package cl.cadcc.ramitos.model

import cl.cadcc.ramitos.model
import doobie.*
import doobie.postgres.implicits.*

import java.time.Instant
import cl.cadcc.ramitos.model.implicits.given

case class Review(
    id: Int,
    accountId: Int,
    courseCode: String,
    comments: String,
    difficulty: Option[Int],
    load: Option[Int],
    utility: Option[Int],
    interest: Option[Int],
    tags: Vector[String],
    createdAt: Instant
) derives Read, Write

object Review {
    object Table extends TableDefinition("reviews") {
        lazy val columns = Composite(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

        val id: Column[Int] = Column("id")
        val accountId: Column[Int] = Column("account_id")
        val courseCode: Column[String] = Column("course_code")
        val comments: Column[String] = Column("comments")
        val difficulty: Column[Option[Int]] = Column("difficulty")
        val load: Column[Option[Int]] = Column("load")
        val utility: Column[Option[Int]] = Column("utility")
        val interest: Column[Option[Int]] = Column("interest")
        val tags: Column[Vector[String]] = Column("tags")
        val createdAt: Column[Instant] = Column("created_at")

        def stat(stat: Stat): Column[Option[Int]] =
            stat match {
                case model.Stat.Difficulty => difficulty
                case model.Stat.Load => load
                case model.Stat.Utility => utility
                case model.Stat.Interest => interest
            }

        object all extends WithSQLDefinition[Review](Composite((
            id.sqlDef,
            accountId.sqlDef,
            courseCode.sqlDef,
            comments.sqlDef,
            difficulty.sqlDef,
            load.sqlDef,
            utility.sqlDef,
            interest.sqlDef,
            tags.sqlDef,
            createdAt.sqlDef
        ))(Review.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[Review](this)
    }
}
