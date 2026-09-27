package cl.cadcc.ramitos.repository

import cats.syntax.all.*, cats.effect.syntax.all.*
import java.time.Instant
import java.util.UUID
import cats.effect.Fiber
import cats.effect.Ref
import cats.effect.std.Supervisor
import doobie.util.Get
import doobie.util.Put
import io.circe.generic.auto.given
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import doobie.WeakAsync
import doobie.ConnectionIO
import cats.data.OptionT
import cats.effect.MonadCancelThrow
import doobie.util.transactor.Transactor
import cats.effect.std.AtomicCell
import cats.effect.Unique
import cats.effect.Temporal
import java.time.temporal.TemporalQuery
import java.time.Duration
import scala.jdk.DurationConverters.given
import cats.effect.kernel.Outcome
import doobie.implicits.given
import cats.effect.Resource

trait MufasaPoller[F[_]] private[repository] {
  def schedule(at: Instant, cause: MufasaPoller.ScheduleReason): F[Unit]
  def getStatus: F[MufasaPoller.Status[F]]
}

object MufasaPoller {
  enum ScheduleReason {
    case Automatic
    case SelfAutomatic
    case Manual
  }

  enum Status[F[_]] {
    case Scheduled(when: Instant, fiber: Fiber[F, Throwable, Unit], identity: Unique.Token, reason: ScheduleReason)
    case Running(fiber: Fiber[F, Throwable, Unit], identity: Unique.Token, reason: ScheduleReason)
    case NotScheduled()
  }

  sealed abstract class Error(val message: String, val cause: Throwable) extends Exception(message, cause)
  case class AlreadyRunning() extends Error("Polling was already running", null)
  case class SchedulingError(private val _message: String) extends Error(_message, null)
  case class OtherError(private val _message: String, private val _cause: Throwable) extends Error(_message, _cause)
  case class InitializationError() extends Error("The state was not properly initialized", null)

  def selfScheduling[
    F[_]: {Temporal, Transactor, Supervisor},
    CacheKey: MufasaCacheKeyRepository
  ](update: Option[CacheKey] => F[(CacheKey, Option[Instant])]): F[MufasaPoller[F]] =
    for {
      sto <- AtomicCell[F].of(Status.NotScheduled[F]())
      poller <- MufasaSelfSchedulingPoller[F, CacheKey](sto, update).pure
      _ <- initialize(update(None), poller)
    } yield poller

  private def initialize[
    F[_]: {MonadCancelThrow as F, Transactor as xa},
    CacheKey: {MufasaCacheKeyRepository as cacheKeyRepo}
  ](init: F[(CacheKey, Option[Instant])], poller: MufasaPoller[F]): F[Unit] =
    OptionT(cacheKeyRepo.get(false).transact(xa)).flatTapNone {
      for {
        (newKey, nextSchedule) <- init
        _ <- cacheKeyRepo.set(newKey, None).transact(xa)
        _ <- nextSchedule.traverse { schedule => poller.schedule(schedule, ScheduleReason.SelfAutomatic) }
      } yield ()
    }.value.void

  trait Mixin[F[_]](self: MufasaPoller[F]) extends MufasaPoller[F] {
    override def schedule(at: Instant, cause: MufasaPoller.ScheduleReason): F[Unit] = self.schedule(at, cause)
    override def getStatus: F[Status[F]] = self.getStatus
  }

  private class MufasaSelfSchedulingPoller[
    F[_]: {
      Temporal as F,
      Transactor as xa,
      Supervisor as runner
    },
    CacheKey : {MufasaCacheKeyRepository as cacheKeyRepo}
  ](
    statusSto: AtomicCell[F, Status[F]],
    update: Option[CacheKey] => F[(CacheKey, Option[Instant])]
  ) extends MufasaPoller[F] {
    private val keyName = cacheKeyRepo.key

    private def guardedUpdate(at: Instant, identity: Unique.Token): F[Unit] =
      F.uncancelable { poll =>
        for {
          now <- F.realTimeInstant
          durRaw = Duration.between(now, at)
          dur = if durRaw.isNegative() then Duration.ZERO else durRaw
          _ <- poll(F.sleep(dur.toScala))
          _ <- poll(statusSto.evalUpdate {
            case Status.Scheduled(_, fiber, token, cause) if identity == token => Status.Running(fiber, token, cause).pure
            case _ : Status.Scheduled[F] => F.raiseError(SchedulingError("The task was re-scheduled"))
            case _ : Status.Running[F] => F.raiseError(SchedulingError("And update job is already running... this should never happen!"))
            case _ : Status.NotScheduled[F] => F.raiseError(SchedulingError("The job was not scheduled... but is running right now... this should never happen!"))
          })
          cacheDataOpt <- poll(cacheKeyRepo.get(false).transact(xa)).guaranteeCase {
            case Outcome.Succeeded(_) => F.unit
            case Outcome.Errored(err) =>
              statusSto.modify {
                case Status.Running(_, token, _) if identity == token => (
                  Status.NotScheduled(),
                  F.raiseError(OtherError("Exception while getting previous cache key", err))
                )
                case _ => (Status.NotScheduled(), F.raiseError(SchedulingError("The update job disappeared while running...")))
              }.flatten
            case Outcome.Canceled() =>
              statusSto.modify {
                case Status.Running(_, token, _) if identity == token => (
                  Status.NotScheduled(),
                  F.unit
                )
                case _ => (Status.NotScheduled(), F.raiseError(SchedulingError("The update job disappered while running...")))
              }.flatten
          }
          cacheData <- cacheDataOpt match {
            case Some(value) => value.pure
            case None =>
              statusSto.modify {
                case Status.Running(_, token, _) if identity == token => (
                  Status.NotScheduled(),
                  F.raiseError(InitializationError())
                )
                case _ => (
                  Status.NotScheduled(),
                  F.raiseError(SchedulingError("The update job disapperead while running..."))
                )
              }.flatten
          }
          updateJob = update(cacheData.value.some)
          (newCacheKey, newSchedule) <- poll(updateJob).guaranteeCase {
            case Outcome.Succeeded(_) => F.unit
            case _ =>
              statusSto.modify {
                case s @ Status.Running(_, token, _) if identity == token => (s, F.unit)
                case s : Status.Scheduled[F] => (s, F.raiseError(SchedulingError("The update job was overriten... this should never happen!")))
                case s => (s, F.raiseError(SchedulingError("The update job disappeared while running...")))
              }.flatten
          }
          _ <- poll(cacheKeyRepo.set(newCacheKey, newSchedule).transact(xa)).guaranteeCase {
            case Outcome.Succeeded(_) => F.unit
            case _ =>
              statusSto.modify {
                case s @ Status.Running(_, token, _) if identity == token => (Status.NotScheduled(), F.unit)
                case s => (s, F.raiseError(SchedulingError("The update job disappeared while running...")))
              }.flatten
          }
          _ <- newSchedule match {
            case Some(schedule) =>
              statusSto.evalModify {
                case Status.Running(_, token, _) if identity == token =>
                  for {
                    newToken <- F.unique
                    fiber <- runner.supervise(guardedUpdate(schedule, newToken))
                  } yield (Status.Scheduled(schedule, fiber, newToken, ScheduleReason.SelfAutomatic), F.unit)
                case s => (s, F.raiseError(SchedulingError("The update job disappeared while running..."))).pure
              }.flatten
            case None =>
              statusSto.modify {
                case Status.Running(_, token, _) if identity == token =>
                  (Status.NotScheduled(), F.unit)
                case s => (s, F.raiseError(SchedulingError("The update job disappeared while running...")))
              }.flatten
          }
        } yield ()
      }

    override def schedule(at: Instant, cause: ScheduleReason = ScheduleReason.Manual): F[Unit] =
      val upd = for {
        dataOpt <- cacheKeyRepo.get(forUpdate = true)
        data <- OptionT.fromOption[ConnectionIO](dataOpt).getOrRaise(Exception(s"Mufasa cache $keyName is not properly initialized!"))
        _ <- cacheKeyRepo.set(data.value, at.some)
      } yield ()
      statusSto.evalUpdate {
        case Status.Scheduled(when, fiber, oldToken, _) =>
          (F uncancelable { poll =>
            for {
              _ <- poll(fiber.cancel)
              _ <- poll(cacheKeyRepo.setSchedule(at).transact(xa))
              token <- F.unique
              newFiber <- runner.supervise(guardedUpdate(when, token))
            } yield Status.Scheduled(at, newFiber, token, cause)
          }).guaranteeCase {
            case Outcome.Succeeded(_) => F.unit
            case _ => statusSto.update {
              case Status.Scheduled(_, _, `oldToken`, _) => Status.NotScheduled()
              case s => s
            }
          }
        case _ : Status.Running[F] =>
          F.raiseError(AlreadyRunning())
        case Status.NotScheduled() =>
          F uncancelable { poll =>
            for {
              _ <- poll(cacheKeyRepo.setSchedule(at).transact(xa))
              token <- F.unique
              fiber <- runner.supervise(guardedUpdate(at, token))
            } yield Status.Scheduled(at, fiber, token, cause)
          }
      }

    override def getStatus: F[Status[F]] = statusSto.get
  }
}
