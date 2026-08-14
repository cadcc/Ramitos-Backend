package cl.cadcc.ramitos.utils

import cl.cadcc.ramitos.model.AccountRole as ModelRole
import cl.cadcc.ramitos.schema.{AccountRole, Ordering}
import cats.syntax.all.*
import cats.mtl.Ask
import cats.MonadThrow

import scala.util.Try
import scala.util.Failure
import scala.util.Success
import cats.MonadError
import cl.cadcc.ramitos.repository.SqlOrder
import cl.cadcc.ramitos.utils.Shapeless.SchemaModelConvert

object extensions {
    extension[F[_], E] (local: Ask[F, Option[E]])
        def toGetSome(errorMessage: String)(using MonadThrow[F]) =
            AskOptionGet(errorMessage, local)

    extension[T] (tried: Try[T])
        def getM[F[_]](using MonadThrow[F]): F[T] =
            tried match
                case Failure(exception) => MonadThrow[F].raiseError(exception)
                case Success(value) => value.pure

    extension[Throw <: Throwable, T] (either: Either[Throw, T])
        def getM[F[_]](using F: MonadError[F, Throw]): F[T] =
            either match
                case Left(value) => F.raiseError(value)
                case Right(value) => value.pure

    extension[Model] (model: Model) {
        def toSchema[Schema](using conv: SchemaModelConvert[Model, Schema]): Schema =
            conv.convert(model)
    }
    
    extension (role: AccountRole)
        def toModel: ModelRole = role match {
            case AccountRole.NONE  => ModelRole.NONE
            case AccountRole.STATS => ModelRole.STATS
            case AccountRole.MOD   => ModelRole.MOD
            case AccountRole.ADMIN => ModelRole.ADMIN
        }

    extension (ordering: Ordering) {
        def toModel: SqlOrder = ordering match {
            case Ordering.ASCENDING  => SqlOrder.ASCENDING
            case Ordering.DESCENDING => SqlOrder.DESCENDING
        }
    }
}
