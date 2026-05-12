package cl.cadcc.ramitos.model

import cl.cadcc.ramitos.model.Stat
import doobie.*
import doobie.postgres.implicits.*

import java.time.Instant
import cl.cadcc.ramitos.model.implicits.given

case class Review(
    id: Int,
    accountId: Int,
    courseCode: String,
    comments: Option[String],
    stats: Map[Stat, Option[Byte]],
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
        val comments: Column[Option[String]] = Column("comments")
        val docencia: Column[Option[Byte]] = Column("docencia")
        val vibes: Column[Option[Byte]] = Column("vibes")
        val relevancia: Column[Option[Byte]] = Column("relevancia")
        val carga: Column[Option[Byte]] = Column("carga")
        val dificultad: Column[Option[Byte]] = Column("dificultad")
        val tags: Column[Vector[String]] = Column("tags")
        val createdAt: Column[Instant] = Column("created_at")

        def stat(stat: Stat): Column[Option[Byte]] =
            stat match {
                case Stat.DOCENCIA => docencia
                case Stat.VIBES => vibes
                case Stat.RELEVANCIA => relevancia
                case Stat.CARGA => carga
                case Stat.DIFICULTAD => dificultad
            }
            
        private def toModel(
            id: Int,
            accountId: Int,
            courseCode: String,
            comments: Option[String],
            docencia: Option[Byte],
            vibes: Option[Byte],
            relevancia: Option[Byte],
            carga: Option[Byte],
            dificultad: Option[Byte],
            tags: Vector[String],
            createdAt: Instant) =
            Review(
                id,
                accountId,
                courseCode,
                comments,
                Map(
                    Stat.DOCENCIA -> docencia,
                    Stat.VIBES -> vibes,
                    Stat.RELEVANCIA -> relevancia,
                    Stat.CARGA -> carga,
                    Stat.DIFICULTAD -> dificultad,
                ),
                tags,
                createdAt
            )
            
        private def ofModel(m: Review) =
            (
                m.id, m.accountId, m.courseCode, m.comments,
                m.stats(Stat.DOCENCIA), m.stats(Stat.VIBES),
                m.stats(Stat.RELEVANCIA), m.stats(Stat.CARGA),
                m.stats(Stat.DIFICULTAD), m.tags, m.createdAt
            )
        
        object all extends WithSQLDefinition[Review](Composite((
            id.sqlDef,
            accountId.sqlDef,
            courseCode.sqlDef,
            comments.sqlDef,
            docencia.sqlDef,
            vibes.sqlDef,
            relevancia.sqlDef,
            carga.sqlDef,
            dificultad.sqlDef,
            tags.sqlDef,
            createdAt.sqlDef
        ))(toModel)(ofModel)) with TableDefinition.RowHelpers[Review](this)
    }
}
