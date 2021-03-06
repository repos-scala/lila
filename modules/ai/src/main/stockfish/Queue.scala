package lila.ai
package stockfish

import scala.concurrent.duration._
import scala.concurrent.Future

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.util.Timeout

import actorApi._
import lila.analyse.{ AnalysisMaker, Info }
import lila.hub.actorApi.ai.GetLoad

private[ai] final class Queue(config: Config) extends Actor {

  private val process = Process(config.execPath, "stockfish") _
  private val actor = context.actorOf(Props(new ActorFSM(process, config)))
  private val monitor = context.actorOf(Props(new Monitor(self)))
  private var actorReady = false

  private def blockAndMeasure[A](fa: Fu[A])(implicit timeout: Timeout): A = {
    val start = nowMillis
    val result = fa await timeout
    monitor ! AddTime((nowMillis - start).toInt)
    result
  }

  def receive = {

    case GetLoad ⇒ {
      import makeTimeout.short
      monitor ? GetLoad pipeTo sender
    }

    case req: PlayReq ⇒ {
      implicit val timeout = makeTimeout((config moveTime req.level).millis + 1.second)
      blockAndMeasure {
        actor ? req mapTo manifest[Valid[String]] map sender.!
      }
    }

    case req: AnalReq ⇒ {
      implicit val timeout = makeTimeout(config.analyseMoveTime + 1.second)
      (actor ? req) mapTo manifest[Valid[Int ⇒ Info]] map sender.! await timeout
    }

    case FullAnalReq(uciMoves, fen) ⇒ {
      implicit val timeout = makeTimeout(config.analyseTimeout)
      type Result = Valid[Int ⇒ Info]
      val moves = uciMoves.split(' ').toList
      val futures = (1 to moves.size - 1).toStream map moves.take map { serie ⇒
        self ? AnalReq(serie.init mkString " ", serie.last, fen) mapTo manifest[Result]
      }
      lila.common.Future.lazyFold(futures)(Vector[Result]())(_ :+ _) addFailureEffect {
        case e ⇒ sender ! Status.Failure(e)
      } map {
        _.toList.sequence map { infos ⇒
          AnalysisMaker(infos.zipWithIndex map (x ⇒ x._1 -> (x._2 + 1)) map {
            case (info, turn) ⇒ (turn % 2 == 1).fold(
              info(turn),
              info(turn) |> { i ⇒ i.copy(score = i.score map (_.negate)) }
            )
          }, true, none)
        }
      } pipeTo sender
    }
  }
}
