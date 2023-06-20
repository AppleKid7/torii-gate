package org.torii_gate.db

import org.torii_gate.UserId
import org.torii_gate.User
import zio.*

sealed trait UserRepo {
  def getUserById(id: UserId): IO[DbErrors, User]
}

object UserRepo {
}
