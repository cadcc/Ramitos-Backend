package cl.cadcc.ramitos.model

import cats.Show
import cats.derived.*

import java.time.LocalDateTime
import cats.syntax.all.*
import cats.implicits.given
import doobie.{Column, Composite, Meta, Put, SQLDefinition, TableDefinition, WithSQLDefinition}
import doobie.postgres.implicits.*
import doobie.util.{Get, Read, Write}

import java.time.Instant
import cl.cadcc.ramitos.model.implicits.given
import io.circe.{Codec, Json}
import io.circe.syntax.*


enum Stat(val s: String) {
    case DOCENCIA extends Stat("docencia")
    case VIBES extends Stat("vibes")
    case RELEVANCIA extends Stat("relevancia")
    case CARGA extends Stat("carga")
    case DIFICULTAD extends Stat("dificultad")
}

object Stat {
    def ofString(s: String): Option[Stat] = s match
        case "docencia" => Stat.DOCENCIA.some
        case "vibes" => Stat.VIBES.some
        case "relevancia" => Stat.RELEVANCIA.some
        case "carga" => Stat.CARGA.some
        case "dificultad" => Stat.DIFICULTAD.some
        case _ => None
}

case class CourseStat(rate: Float, count: Int, sum: Long) derives Codec, Show

case class Course(
    code: String,
    name: String,
    stats: Map[Stat, CourseStat],
    tagStats: Map[String, CourseStat],
    createdAt: Instant,
    updatedAt: Instant
) derives Read, Write

object Course {

    object Table extends TableDefinition("courses") {
        lazy val columns = Composite(all)
        lazy val columnNames = all.columns.map(_.rawName).toVector

        val code: Column[String] = Column("code")
        val displayName: Column[String] = Column("name")

        val stats: Column[Map[Stat, CourseStat]] = Column("stats")
        
        val tagStats: Column[Map[String, CourseStat]] = Column("tag_stats")

        val createdAt: Column[Instant] = Column("createdAt")
        val updatedAt: Column[Instant] = Column("updated_at")

        object all extends WithSQLDefinition[Course](Composite((
            code.sqlDef,
            displayName.sqlDef,
            stats.sqlDef,
            tagStats.sqlDef,
            createdAt.sqlDef,
            updatedAt.sqlDef
        ))(Course.apply)(Tuple.fromProductTyped)) with TableDefinition.RowHelpers[Course](this)
    }
}
