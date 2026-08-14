package cl.cadcc.ramitos.utils

import scala.deriving.Mirror
import scala.Tuple.:*
import smithy4s.time.Timestamp

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.util.NotGiven

object Shapeless {

    trait SchemaModelConvert[Src, Dst] {
        def convert(s: Src): Dst
    }

    def toSchema[Src, Dst](x: Src)(using conv: SchemaModelConvert[Src, Dst]): Dst =
        conv.convert(x)

    given idConv[T1 <: T2, T2]: SchemaModelConvert[T1, T2] = SchemaModelConvert.instance(identity)

    given consConv[HSrc, TSrc <: Tuple, HDst, TDst <: Tuple](using NotGiven[(HSrc *: TSrc) <:< (HDst *: TDst)])(using hdConv: SchemaModelConvert[HSrc, HDst], tlConv: SchemaModelConvert[TSrc, TDst]): SchemaModelConvert[HSrc *: TSrc, HDst *: TDst] =
        SchemaModelConvert.instance(tup =>
            val x = hdConv.convert(tup.head)
            val y = tlConv.convert(tup.tail)
            x *: y
        )
    
    given timeConv: SchemaModelConvert[LocalDateTime, Timestamp] = SchemaModelConvert.instance(ldt => Timestamp.fromOffsetDateTime(ldt.atOffset(ZoneOffset.UTC) ))

    object SchemaModelConvert {
        def apply[Src, Dst](using ev: SchemaModelConvert[Src, Dst]) = ev

        def instance[Src, Dst](f: Src => Dst) = new SchemaModelConvert[Src, Dst] {
            def convert(s: Src) = f(s)
        }
    }
}
