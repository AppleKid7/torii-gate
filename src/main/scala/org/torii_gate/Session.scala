package org.torii_gate

import com.devsisters.shardcake.Sharding
import dev.profunktor.redis4cats.RedisCommands
import java.util.UUID
import org.torii_gate.MatchBehavior.{MatchMakingError, MatchMessage}
import zio.*

trait Session {
  def createSession(): IO[MatchMakingError, SessionId]
  def joinSession(sessionId: SessionId): ZIO[Sharding, MatchMakingError, Set[String]]
  def leaveSession(sessionId: SessionId, userId: UserId): ZIO[Sharding, MatchMakingError, String]
  def getAllUsers(sessionId: SessionId): ZIO[Sharding, MatchMakingError, Set[UserId]]
  def getAllSessions: ZIO[Sharding, MatchMakingError, Set[SessionId]]
}

object Session {
  val live: ZLayer[Sharding, MatchMakingError, Session] = SessionLive.layer

  def createSession() = ZIO.environmentWithZIO[Session](_.get.createSession())

  def joinSession(sessionId: SessionId) =
    ZIO.environmentWithZIO[Session](_.get.joinSession(sessionId))

  def leaveSession(sessionId: SessionId, userId: UserId) =
    ZIO.environmentWithZIO[Session](_.get.leaveSession(sessionId, userId))

  def getAllUsers(sessionId: SessionId) =
    ZIO.environmentWithZIO[Session](_.get.getAllUsers(sessionId))

  def getAllSessions =
    ZIO.environmentWithZIO[Session](_.get.getAllSessions)
}

case class SessionLive(
    matchShard: com.devsisters.shardcake.Messenger[MatchMessage]
) extends Session {
  override def createSession() =
    for {
      uuid <- Random.nextUUID
    } yield SessionId(SessionLive.matchId(uuid.toString))

  override def joinSession(sessionId: SessionId) =
    for {
      uuid <- Random.nextUUID
      res <- matchShard
        .send[Either[MatchMakingError, Set[String]]](sessionId.id)(
          MatchMessage.Join(SessionLive.userId(uuid.toString), _)
        )
        .orDie
      value <- ZIO.fromEither(res)
    } yield value

  override def leaveSession(sessionId: SessionId, userId: UserId) =
    for {
      res <- matchShard
        .send[Either[MatchMakingError, String]](sessionId.id)(
          MatchMessage.Leave(userId.id, _)
        )
        .mapError(e => MatchMakingError.ShardcakeConnectionError(e.getMessage()))
      value <- ZIO.fromEither(res)
    } yield value

  override def getAllUsers(sessionId: SessionId) =
    for {
      res <- matchShard
        .send[Either[MatchMakingError, Set[UserId]]](sessionId.id)(
          MatchMessage.ListUsers(_)
        )
        .mapError(e => MatchMakingError.ShardcakeConnectionError(e.getMessage()))
      value <- ZIO.fromEither(res)
    } yield value

  override def getAllSessions =
    for {
      matchShard <- Sharding.messenger(MatchBehavior.Match)
      res <- matchShard
        .send[Either[MatchMakingError, Set[SessionId]]]("")(
          MatchMessage.ListSessions(_)
        )
        .orDie
      value <- ZIO.fromEither(res)
    } yield value
}

object SessionLive {
  val layer: ZLayer[Sharding, MatchMakingError, Session] = ZLayer.scoped {
    for {
      matchShard <- Sharding.messenger[MatchMessage](MatchBehavior.Match)
    } yield SessionLive(matchShard)
  }

  def matchId(id: String): String =
    s"match:$id"

  def userId(id: String): String =
    s"user-$id"
}
