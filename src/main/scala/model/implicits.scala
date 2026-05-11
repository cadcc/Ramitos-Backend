package cl.cadcc.ramitos.model

import cats.syntax.all.*
import cats.Show
import doobie.postgres.{Instances, PostgresJavaTimeMetaInstances, free, syntax}
import doobie.postgres.circe.Instances.JsonbInstances
import doobie.util.{Get, Put}
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*

private[model] object implicits
    extends JsonbInstances
    with Instances
    with free.Instances
    with syntax.ToPostgresMonadErrorOps
    with syntax.ToFragmentOps
    with syntax.ToPostgresExplainOps
    with PostgresJavaTimeMetaInstances
{
    import cl.cadcc.ramitos.model.Stat

    given stringMapGet[A: Decoder]: Get[Map[String, A]] = Get[Json].temap { json =>
        for {
            obj <- json.asObject.toRight("Json object was expected to be an object.")
            ans <- obj.toList.traverse { (k, v) => v.as[A].bimap(err => err.show, (k, _)) }
        } yield ans.toMap
    }

    given stringMapPut[A: Encoder]: Put[Map[String, A]] = Put[Json].contramap(_.asJson)

    given vectorGet[A: Decoder]: Get[Vector[A]] = Get[Json].temap { json =>
        for {
            arr <- json.asArray.toRight("Json object was expected to be an array.")
            ans <- arr.traverse(_.as[A].leftMap(_.show))
        } yield ans
    }

    given vectorPut[A: Encoder]: Put[Vector[A]] = Put[Json].contramap(_.asJson)

    given statMapGet[A: {Decoder, Show}]: Get[Map[Stat, A]] = stringMapGet[A].temap { map =>
        map.toList
            .traverse { (k, v) =>
                Stat.ofString(k)
                    .toRight(s"Unknown stat key '$k'.")
                    .map { newK => (newK, v) } }
            .map(_.toMap)
    }

    given statMapPut[A](using Encoder[A]): Put[Map[Stat, A]] = stringMapPut[A].tcontramap { map =>
        map.map { (k, v) => (k.s, v)}
    }
}
