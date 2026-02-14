package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import doobie.{TableDefinition, Column}
import doobie.implicits.javatimedrivernative._

object CoursesTable extends TableDefinition("courses"):
    val code: Column[String] = Column("code")
    val displayName: Column[String] = Column("name")

    val difficulty: Column[Float]    = Column("difficulty")
    val difficultyCount: Column[Int] = Column("difficulty_count")
    val difficultySum: Column[Long]  = Column("difficulty_sum")

    val load: Column[Float]    = Column("load")
    val loadCount: Column[Int] = Column("load_count")
    val loadSum: Column[Long]  = Column("load_sum")

    val utility: Column[Float]    = Column("utility")
    val utilityCount: Column[Int] = Column("utility_count")
    val utilitySum: Column[Long]  = Column("utility_sum")

    val interest: Column[Float]    = Column("interest")
    val interestCount: Column[Int] = Column("interest_count")
    val interestSum: Column[Long]  = Column("interest_sum")
