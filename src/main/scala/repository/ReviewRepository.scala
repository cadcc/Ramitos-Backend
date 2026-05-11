package cl.cadcc.ramitos.repository

import cats.syntax.all.*
import cats.data.NonEmptyList
import cl.cadcc.ramitos.model.{Review, Stat}
import doobie.syntax.all.*
import doobie.{ConnectionIO, Fragment}
import fs2.Stream

import scala.language.implicitConversions

trait ReviewRepository {
    def getById(id: Int): ConnectionIO[Option[Review]]
    def list(limit: Long, from: Option[Int] = None): Stream[ConnectionIO, Review]
    def create(
        accountId: Int,
        courseCode: String,
        comments: String,
        stats: Map[Stat, Byte],
        tags: Vector[String],
    ): ConnectionIO[Review]
}

object ReviewRepository {


    private class ReviewRepositoryImpl extends ReviewRepository {
        private val Table = Review.Table

        def getById(id: Int): ConnectionIO[Option[Review]] =
            sql"SELECT * FROM $Table WHERE ${Table.id === id}".query[Review].option

        def list(limit: Long, from: Option[Int] = None): Stream[ConnectionIO, Review] = {
            val sql =
                fr"SELECT * FROM $Table "
                    ++ from.fold(fr"")(v => fr"WHERE ${Table.id < v} ")
                ++
                fr"ORDER BY $Table.id ASC"
            sql.query[Review].stream
        }

        def create(
            accountId: Int,
            courseCode: String,
            comments: String,
            stats: Map[Stat, Byte],
            tags: Vector[String],
        ): ConnectionIO[Review] = {
            val upds: NonEmptyList[(Fragment, Fragment)] = NonEmptyList.of(
                Table.accountId --> accountId,
                Table.courseCode --> courseCode,
                Table.comments --> comments
            )

            val statUpds: List[(Fragment, Fragment)] = stats.toList.map( (stat, value) => Table.stat(stat) --> value.some)

            Table.insertInto(upds ++ statUpds).update.withUniqueGeneratedKeys[Review](Table.columnNames*)
        }
    }
}
