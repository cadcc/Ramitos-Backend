package cl.cadcc.ramitos.model

import cats.syntax.all.*
import cats.implicits.*
import io.circe.Codec
import doobie.util.Read
import doobie.util.Write
import doobie.util.meta.Meta
import doobie.postgres.implicits.pgEnumStringOpt
import cats.Order
import fs2.Stream
import fs2.Pure

enum SemesterType(val s: String, val index: Int) derives Codec {
  case YEAR extends SemesterType("year", 0)
  case FALL extends SemesterType("fall", 1)
  case SPRING extends SemesterType("spring", 2)
  case SUMMER extends SemesterType("summer", 3)

  def next: SemesterType = this match {
    case YEAR => FALL
    case FALL => SPRING
    case SPRING => SUMMER
    case SUMMER => YEAR
  }

  def prev: SemesterType = this match {
    case YEAR => SUMMER
    case FALL => YEAR
    case SPRING => FALL
    case SUMMER => SPRING
  }
}

object SemesterType {
  given Order[SemesterType] = Order.by(_.index)
  
  given Meta[SemesterType] = pgEnumStringOpt[SemesterType]("semester", ofString, _.s)

  def ofString(s: String): Option[SemesterType] = s match {
    case "year" => YEAR.some
    case "fall" => FALL.some
    case "spring" => SPRING.some
    case _ => None
  }

  def ofIndex(i: Int): Option[SemesterType] = i match {
    case 0 => YEAR.some
    case 1 => FALL.some
    case 2 => SPRING.some
    case 3 => SUMMER.some
    case _ => None
  }
}

case class Semester(year: Short, semesterType: SemesterType) derives Codec, Read, Write {
  def addYears(years: Short): Semester = this.copy(year = (year + years).toShort)

  def minusYears(years: Short): Semester = this.copy(year = (year - years).toShort)

  def next: Semester =
    val y = semesterType match {
      case SemesterType.SUMMER => (year + 1).toShort
      case _ => year
    }
    Semester(y, semesterType.next)

  def prev: Semester =
    val y = semesterType match {
      case SemesterType.YEAR => (year - 1).toShort
      case _ => year
    }
    Semester(y, semesterType.prev)
}

object Semester {
  given Order[Semester] = Order.by(Tuple.fromProductTyped)

  def between[F[_]](start: Semester, end: Semester): Stream[F, Semester] =
    Stream.iterate(start)(_.next)
      .takeWhile(_ <= end)
}
