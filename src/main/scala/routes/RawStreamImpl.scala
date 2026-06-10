package cl.cadcc.ramitos.routes

import cats.syntax.all.*
import cats.effect.Sync
import cl.cadcc.ramitos.schema.{ByteStream, RawByteStreamsServiceGen, RawGetCoursesStaticDataInput, RawGetCoursesStaticDataOutput}
import fs2.Stream
import fs2.io.readInputStream
import smithy4s.time.Timestamp

object RawStreamImpl {
    type SOMatch[T] = T match {
        case ByteStream => Byte
    }
    type Streamming[F[_]] = [_, _, O, _, SO] =>> F[(O, Stream[F, SOMatch[SO]])]

    def make[F[_]: Sync]: RawByteStreamsServiceGen[Streamming[F]] = RawStreamImpl[F]

    private class RawStreamImpl[F[_]: Sync as F] extends RawByteStreamsServiceGen[Streamming[F]] {

        /** This operation uses {@literal @}streaming on the output (content).
         *
         * HTTP GET /api/courses.json
         */
        override def rawGetCoursesStaticData(): F[(RawGetCoursesStaticDataOutput, Stream[F, Byte])] = {
            val stream = readInputStream(
                F.blocking { getClass.getClassLoader.getResourceAsStream("courses.json") },
                chunkSize = 4096
            )
            (RawGetCoursesStaticDataOutput(), stream).pure
        }
    }
}
