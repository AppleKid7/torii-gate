package org.torii_gate.db

import org.torii_gate.{User, UserId}
import zio.*
import zio.jdbc.*

sealed trait UserRepo {
  def getUserById(id: UserId): IO[DbErrors, User]
}

object UserRepo {
  val live: ZLayer[ZConnectionPool, DbErrors, UserRepo] = JDBCUserRepo.layer

  def getUserById(id: UserId) = ZIO.environmentWithZIO[UserRepo](_.get.getUserById(id))
}

case class JDBCUserRepo(
  connectionPool: ZConnectionPool
) extends UserRepo {
  import User.{given JdbcDecoder[User]}

  override def getUserById(userId: UserId): IO[DbErrors, User] = ???
    // for {
    //   value <- connectionPool.transaction {
    //     sql"select * from users where id = ${userId.id}"
    //       .query[User]
    //       .selectOne
    //   }
    // } yield value
}

object JDBCUserRepo {
  val layer: ZLayer[ZConnectionPool, DbErrors, UserRepo] = ??? /*ZLayer.scoped {
    for {
      sharding <- ZIO.service[Sharding]
    } yield SessionLive(sharding)
  }*/
}
