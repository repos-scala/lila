package lila.db

import play.api.libs.json._
import Json.JsValueWrapper

import reactivemongo.api._
import reactivemongo.bson.BSONDocument

import play.modules.reactivemongo.Implicits._

trait DbApi extends operator {
  object select extends operator with select
  object sort extends sort
}

trait operator {

  def $set[A : Writes](pairs: (String, A)*) = Json.obj("$set" -> Json.obj(wrap(pairs): _*))
  def $set(pairs: (String, JsValueWrapper)*) = Json.obj("$set" -> Json.obj(pairs: _*))
  def $inc[A : Writes](pairs: (String, A)*) = Json.obj("$inc" -> Json.obj(wrap(pairs): _*))

  def $gt[A: Writes](value: A) = Json.obj("$gt" -> value)
  def $gte[A: Writes](value: A) = Json.obj("$gte" -> value)
  def $lt[A: Writes](value: A) = Json.obj("$lt" -> value)
  def $lte[A: Writes](value: A) = Json.obj("$lte" -> value)

  def $in[A: Writes](values: Seq[A]) = Json.obj("$in" -> Json.arr(values))

  private def wrap[K, V : Writes](pairs: Seq[(K, V)]): Seq[(K, JsValueWrapper)] = pairs map {
    case (k, v) ⇒ k -> Json.toJsFieldJsValueWrapper(v)
  }
}

object select extends operator with select
trait select { self: operator ⇒

  def apply[A: Writes](id: A) = byId(id)

  def byId[A: Writes](id: A) = Json.obj("_id" -> id)

  def byIds[A: Writes](ids: Seq[A]) = Json.obj("_id" -> $in(ids))
}

object sort extends sort
trait sort {

  def naturalDesc = "$natural" -> desc

  def asc = SortOrder.Ascending
  def desc = SortOrder.Descending
}