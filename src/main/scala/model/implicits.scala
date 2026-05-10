package cl.cadcc.ramitos.model

import cats.syntax.all.*
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
    given stringMapGet[A](using Decoder[A]): Get[Map[String, A]] = Get[Json].temap { json =>
        for {
            obj <- json.asObject.toRight("Json object was expected to be an object.")
            ans <- obj.toList.traverse { (k, v) => v.as[A].bimap(err => err.show, (k, _)) }
        } yield ans.toMap
    }

    given stringMapPut[A](using Encoder[A]): Put[Map[String, A]] = Put[Json].contramap(_.asJson)

    given vectorGet[A](using Decoder[A]): Get[Vector[A]] = Get[Json].temap { json =>
        for {
            arr <- json.asArray.toRight("Json object was expected to be an array.")
            ans <- arr.traverse(_.as[A].leftMap(_.show))
        } yield ans
    }

    given vectorPut[A](using Encoder[A]): Put[Vector[A]] = Put[Json].contramap(_.asJson)
}
