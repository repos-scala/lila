package lila
package team

import scalaz.effects._
import org.scala_tools.time.Imports._
import com.github.ornicar.paginator.Paginator

import user.{ User, UserRepo }
import http.Context

final class TeamApi(
    teamRepo: TeamRepo,
    memberRepo: MemberRepo,
    requestRepo: RequestRepo,
    userRepo: UserRepo,
    paginator: PaginatorBuilder) {

  val creationPeriod = 1 week

  def create(setup: TeamSetup, me: User): IO[Team] = setup.trim |> { s ⇒
    Team(
      name = s.name,
      location = s.location,
      description = s.description,
      open = s.isOpen,
      createdBy = me) |> { team ⇒
        for {
          _ ← teamRepo saveIO team
          _ ← memberRepo.add(team.id, me.id)
        } yield team
      }
  }

  def mine(me: User): IO[List[Team]] = for {
    teamIds ← memberRepo teamIdsByUserId me.id
    teams ← teamRepo byOrderedIds teamIds
  } yield teams

  def hasCreatedRecently(me: User): IO[Boolean] =
    teamRepo.userHasCreatedSince(me.id, creationPeriod)

  def isMine(team: Team)(implicit ctx: Context): IO[Boolean] =
    ~ctx.me.map(me ⇒ belongsTo(team, me))

  def relationTo(team: Team)(implicit ctx: Context): IO[TeamRelation] = ~ctx.me.map(me ⇒
    for {
      mine ← isMine(team)
      request ← requestRepo.find(team.id, me.id)
    } yield TeamRelation(mine, request)
  )

  def join(teamId: String)(implicit ctx: Context): IO[Option[Requesting]] = for {
    teamOption ← teamRepo byId teamId
    result ← ~(teamOption |@| ctx.me).tupled.map({
      case (team, user) if team.open ⇒ for {
        exists ← belongsTo(team, user)
        _ ← (for {
          _ ← memberRepo.add(team.id, user.id)
          _ ← teamRepo.incMembers(team.id, +1)
        } yield ()) doUnless exists
      } yield Joined(team).some: Option[Requesting]
      case (team, user) ⇒ io(Motivate(team).some: Option[Requesting])
    })
  } yield result

  def requestable(teamId: String, user: User): IO[Option[Team]] = for {
    teamOption ← teamRepo byId teamId
    able ← ~teamOption.map({ requestable(_, user) })
  } yield teamOption filter (_ ⇒ able)

  def requestable(team: Team, user: User): IO[Boolean] = for {
    exists ← requestRepo.exists(team.id, user.id)
    mine ← belongsTo(team, user)
  } yield !exists && !mine

  def createRequest(team: Team, setup: RequestSetup, user: User): IO[Unit] = for {
    able ← requestable(team, user)
    request = Request(team = team.id, user = user.id, message = setup.message)
    _ ← requestRepo add request doIf able
  } yield ()

  def quit(teamId: String)(implicit ctx: Context): IO[Option[Team]] = for {
    teamOption ← teamRepo byId teamId
    result ← ~(teamOption |@| ctx.me).tupled.map({
      case (team, user) ⇒ for {
        exists ← belongsTo(team, user)
        _ ← (for {
          _ ← memberRepo.remove(team.id, user.id)
          _ ← teamRepo.incMembers(team.id, -1)
        } yield ()) doIf exists
      } yield team.some
    })
  } yield result

  def belongsTo(team: Team, user: User): IO[Boolean] =
    memberRepo.exists(teamId = team.id, userId = user.id)
}