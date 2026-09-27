package cl.cadcc.ramitos.repository

import cats.syntax.all.*
import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import doobie.*
import doobie.syntax.all.*
import doobie.free.connection.ConnectionOp
import cats.free.Free
import io.circe.Json
import io.circe.syntax.*
import doobie.postgres.implicits.given
import doobie.postgres.circe.jsonb.implicits.given
import doobie.util.Get
import doobie.util.Put
import doobie.generic.auto.given
import MufasaCacheKeyRepository.MufasaCacheData
import java.time.Instant

trait MufasaCacheKeyRepository[Value] private[repository] (val key: String) {
  type Data = MufasaCacheData[Value]
  
  def get(forUpdate: Boolean = false): ConnectionIO[Option[Data]]
  def set(value: Value, schedule: Option[Instant]): ConnectionIO[Unit]
  def setSchedule(schedule: Instant): ConnectionIO[Unit]
}

object MufasaCacheKeyRepository {
  case class MufasaCacheData[T](
    key: String,
    value: T,
    schedule: Option[Instant],
  )
  
  def make[T: {Decoder, Encoder}](key: String): MufasaCacheKeyRepository[T] = Make[T](key)
  
  private class Make[T : {Decoder, Encoder}](private val _key: String) extends MufasaCacheKeyRepository[T](_key) {
    private val F = WeakAsync[ConnectionIO]
    private given doobieGet: Get[T] = Get[Json].temap(_.as[T].leftMap(_.show))
    private given doobiePut: Put[T] = Put[Json].contramap(_.asJson)

    private val getQuery = fr"SELECT * FROM mufasa_cache_data WHERE key = $_key"
    
    override def get(forUpdate: Boolean = false): ConnectionIO[Option[Data]] =
      val q = getQuery ++ (if forUpdate then fr"FOR UPDATE SKIP LOCKED" else fr"")
      q.query[Data].option

    override def set(value: T, schedule: Option[Instant]): ConnectionIO[Unit] =
      sql"""
          INSERT INTO mufasa_cache_data(key, value, scheduled) VALUES ($_key, $value, $schedule)
          ON CONFLICT (key) DO UPDATE SET
            value = $value,
            scheduled = $schedule"""
        .update.run
        .ensure(AssertionError("No rows updated... Something is wrong..."))(_ == 1)
        .void

    override def setSchedule(schedule: Instant): ConnectionIO[Unit] =
      sql"UPDATE mufasa_cache_data SET schedule = $schedule WHERE key = $_key"
        .update.run
        .ensure(AssertionError("No rows updated... Something is wrong..."))(_ == 1)
        .void
  }
}
