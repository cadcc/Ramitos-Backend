package cl.cadcc.ramitos.utils

import cl.cadcc.ramitos.model.{AccountRole => ModelRole}
import cl.cadcc.ramitos.schema.AccountRole
import cats.syntax.all._
import cats.mtl.Ask
import cats.MonadThrow
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import cats.MonadError

object extensions {
    extension[F[_], E] (local: Ask[F, Option[E]])
        def toGetSome(errorMessage: String)(using MonadThrow[F]) =
            AskWithUnderlingOption(errorMessage, local)

    extension[T] (tried: Try[T])
        def getM[F[_]](using MonadThrow[F]): F[T] =
            tried match
                case Failure(exception) => MonadThrow[F].raiseError(exception)
                case Success(value) => value.pure

    extension[Throw <: Throwable, T] (either: Either[Throw, T])
        def getM[F[_]](using MonadError[F, Throw]): F[T] =
            either match
                case Left(value) => MonadError[F, Throw].raiseError(value)
                case Right(value) => value.pure
    
    extension (role: AccountRole)
        def toModel() = this match {
            case AccountRole.NONE  => ModelRole.NONE
            case AccountRole.STATS => ModelRole.STATS
            case AccountRole.MOD   => ModelRole.MOD
            case AccountRole.ADMIN => ModelRole.ADMIN
        }
}
