package demo

import scala.Double.NaN
import scala.Double.NegativeInfinity
import scala.Double.PositiveInfinity
import scala.util.Try

import akka.stream.scaladsl.Flow
import io.deftrade.BarSize
import io.deftrade.TickType

import Ib._

case class BarAcc(
  var tsMillis: Long = -1,
  var open: Double = NaN,
  var high: Double = NegativeInfinity,
  var low: Double = PositiveInfinity,
  var close: Double = NaN,
  var volume: Int = 0,
  var total: Double = 0.0)

case class Bar(tsMillis: Long, open: Double, high: Double, low: Double, close: Double, volume: Int, wap: Double)

case class RtVolume(last: Double, size: Int, tsMillis: Long, volume: Int, vwap: Double, single: Boolean)
object RtVolume {
  def from(s: String): Try[RtVolume] = {
    val ss = s split ';'
    Try {
      require(ss.length == 6)
      apply(ss(0).toDouble, ss(1).toInt, ss(2).toLong, ss(3).toInt, ss(4).toDouble, ss(5).toBoolean)
    }
  }
  def unapply(s: String): Option[RtVolume] = from(s).toOption
  implicit val ordering = Ordering by { (rtv: RtVolume) => rtv.tsMillis }
}

object Flows {
  /**
    * Processes a stream of raw ticks into candlesticks (bars).
    * Written to work in the case where, for whatever reason, the RtVolume ticks are not reported
    * strictly sequentially.
    */
  def tickToBar(barSize: BarSize.XVal): Flow[RawTickMessage, Bar, Unit] =
    Flow[RawTickMessage]
      .collect {
        case TickString(_, TickType.RT_VOLUME, RtVolume(rtv)) => rtv
      }
      .groupBy(Int.MaxValue, _.tsMillis / (barSize.secs * 1000)).fold(BarAcc()) { (acc, rtv) =>
        if (acc.tsMillis < 0) acc.tsMillis = rtv.tsMillis
        if (acc.open == NaN) acc.open = rtv.last
        acc.high = acc.high max rtv.last
        acc.low = acc.low min rtv.last
        acc.close = rtv.last
        acc.volume += rtv.volume
        acc.total += rtv.last
        acc
      }
      .mergeSubstreams collect { // paranoid - concatSubstreams should be OK but...
        case BarAcc(tsMillis, open, high, low, close, volume, total) if tsMillis < 0 => // sanity check
          Bar(tsMillis, open, high, low, close, volume, total / volume)
      }

}

object StreamStuff {
  // TODO: put this in a test case
  "701.25;1;1348075489787;67866;701.46914828;true" match {
    case RtVolume(rtv) => println(rtv)
  }

}