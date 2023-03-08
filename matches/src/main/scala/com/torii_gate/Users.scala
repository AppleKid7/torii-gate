package com.torii_gate

import java.util.UUID
import zio.json.*

case class User(id: UUID, name: String)

object User {
  given JsonEncoder[User] =
    DeriveJsonEncoder.gen[User]

  given JsonDecoder[User] =
    DeriveJsonDecoder.gen[User]
}

case class UserLeave(id: UUID)
object UserLeave {
  given JsonEncoder[UserLeave] =
    DeriveJsonEncoder.gen[UserLeave]

  given JsonDecoder[UserLeave] =
    DeriveJsonDecoder.gen[UserLeave]
}

case class UserJoinResponse(success: List[String])
object UserJoinResponse {
  given JsonEncoder[UserJoinResponse] =
    DeriveJsonEncoder.gen[UserJoinResponse]

  given JsonDecoder[UserJoinResponse] =
    DeriveJsonDecoder.gen[UserJoinResponse]
}
