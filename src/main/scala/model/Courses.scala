package cl.cadcc.ramitos.model

import java.time.LocalDateTime
import cats.syntax.all.*
import doobie.{Column, TableDefinition}
import doobie.postgres.implicits.*
import doobie.WithSQLDefinition
import doobie.Composite
import doobie.util.{Read, Write}
import doobie.SQLDefinition

import java.time.Instant
import cl.cadcc.ramitos.model.implicits.given
import io.circe.{Codec, Json}


enum Stat {
    case Difficulty
    case Load
    case Utility
    case Interest
}

case class CourseStat(rate: Float, count: Int, sum: Long) derives Read, Write, Codec
case class Course(
    code: String,
    name: String,
    difficulty: CourseStat,
    load: CourseStat,
    utility: CourseStat,
    interest: CourseStat,
    tagStats: Map[String, CourseStat],
    createdAt: Instant,
    updatedAt: Instant
) derives Read, Write {
    def getStat(stat: Stat) =
        stat match
            case Stat.Difficulty => this.difficulty
            case Stat.Interest => this.interest
            case Stat.Load => this.load
            case Stat.Utility => this.utility
}

object Course {

    object Table extends TableDefinition("courses") {
        lazy val columns = Composite(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

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
        
        val tagStats: Column[Map[String, CourseStat]] = Column("tag_stats")

        val createdAt: Column[Instant] = Column("createdAt")
        val updatedAt: Column[Instant] = Column("updated_at")

        def getStat(stat: Stat): SQLDefinition[CourseStat] = stat match
            case Stat.Difficulty => DifficultyStat
            case Stat.Load => LoadStat
            case Stat.Utility => UtilityStat
            case Stat.Interest => InterestStat

        object DifficultyStat extends WithSQLDefinition[CourseStat](Composite((
            difficulty.sqlDef,
            difficultyCount.sqlDef,
            difficultySum.sqlDef
        ))(CourseStat.apply)(Tuple.fromProductTyped))

        object LoadStat extends WithSQLDefinition[CourseStat](Composite((
            load.sqlDef,
            loadCount.sqlDef,
            loadSum.sqlDef
        ))(CourseStat.apply)(Tuple.fromProductTyped))

        object UtilityStat extends WithSQLDefinition[CourseStat](Composite((
            utility.sqlDef,
            utilityCount.sqlDef,
            utilitySum.sqlDef
        ))(CourseStat.apply)(Tuple.fromProductTyped))

        object InterestStat extends WithSQLDefinition[CourseStat](Composite((
            interest.sqlDef,
            interestCount.sqlDef,
            interestSum.sqlDef
        ))(CourseStat.apply)(Tuple.fromProductTyped))

        object all extends WithSQLDefinition[Course](Composite((
            code.sqlDef,
            displayName.sqlDef,
            DifficultyStat.sqlDef,
            LoadStat.sqlDef,
            UtilityStat.sqlDef,
            InterestStat.sqlDef,
            tagStats.sqlDef,
            createdAt.sqlDef,
            updatedAt.sqlDef
        ))(Course.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[Course](this)
    }
}
