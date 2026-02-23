package cl.cadcc.ramitos.model

import doobie.TableDefinition
import doobie.Column
import doobie.WithSQLDefinition
import doobie.Composite

case class Review(
    id: Int,
    accountId: Int,
    courseCode: String,
    comments: String,
    difficulty: Option[Float],
    load: Option[Float],
    utility: Option[Float],
    interest: Option[Float]
)

object Review {
    object Table extends TableDefinition("reviews") {
        val id: Column[Int] = Column("id")
        val accountId: Column[Int] = Column("account_id")
        val courseCode: Column[String] = Column("course_code")
        val comments: Column[String] = Column("comments")
        val difficulty: Column[Option[Float]] = Column("difficulty")
        val load: Column[Option[Float]] = Column("load")
        val utility: Column[Option[Float]] = Column("utility")
        val interest: Column[Option[Float]] = Column("interest")

        object all extends WithSQLDefinition[Review](Composite((
            id.sqlDef,
            accountId.sqlDef,
            courseCode.sqlDef,
            comments.sqlDef,
            difficulty.sqlDef,
            load.sqlDef,
            utility.sqlDef,
            interest.sqlDef
        ))(Review.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[Review](this)
    }
}
