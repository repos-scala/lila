package lila.socket
package actorApi

import play.api.libs.json.JsObject

case class Connected[M <: SocketMember](
  enumerator: JsEnumerator,
  member: M)
case class Sync(uid: String, friends: List[String])
case class Ping(uid: String)
case class PingVersion(uid: String, version: Int)
case object Broom
case class Quit(uid: String)

case class Fen(gameId: String, fen: String, lastMove: Option[String])
case class LiveGames(uid: String, gameIds: List[String])
case class Resync(uid: String)

// hubs
case object GetVersion

case class SendToFlag(flag: String, message: JsObject) 
