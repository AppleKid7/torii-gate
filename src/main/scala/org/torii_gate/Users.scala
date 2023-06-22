package org.torii_gate

import java.util.UUID
import zio.json.*
import zio.jdbc.JdbcDecoder
import zio.schema.*

case class User(id: UserId, name: String)

object User {
  given JsonEncoder[User] =
    DeriveJsonEncoder.gen[User]

  given JsonDecoder[User] =
    DeriveJsonDecoder.gen[User]

  val schema: Schema[User] =
    DeriveSchema.gen[User]

  given JdbcDecoder[User] =
    JdbcDecoder.fromSchema(schema)
}

case class UserLeave(id: String)
object UserLeave {
  given JsonEncoder[UserLeave] =
    DeriveJsonEncoder.gen[UserLeave]

  given JsonDecoder[UserLeave] =
    DeriveJsonDecoder.gen[UserLeave]
}

case class UserListResponse(success: List[String])
object UserListResponse {
  given JsonEncoder[UserListResponse] =
    DeriveJsonEncoder.gen[UserListResponse]

  given JsonDecoder[UserListResponse] =
    DeriveJsonDecoder.gen[UserListResponse]
}

final case class UserId(id: String)
object UserId {
  given zio.json.JsonEncoder[UserId] =
    DeriveJsonEncoder.gen[UserId]

  given zio.json.JsonDecoder[UserId] =
    DeriveJsonDecoder.gen[UserId]
}

final case class SessionId(id: String)
object SessionId {
  given zio.json.JsonEncoder[SessionId] =
    DeriveJsonEncoder.gen[SessionId]

  given zio.json.JsonDecoder[SessionId] =
    DeriveJsonDecoder.gen[SessionId]
}

case class CreateSessionResponse(sessionId: SessionId)
object CreateSessionResponse {
  given zio.json.JsonEncoder[CreateSessionResponse] =
    DeriveJsonEncoder.gen[CreateSessionResponse]

  given zio.json.JsonDecoder[CreateSessionResponse] =
    DeriveJsonDecoder.gen[CreateSessionResponse]
}

case class JoinSessionResponse(sessionId: SessionId, message: String)
object JoinSessionResponse {
  given zio.json.JsonEncoder[JoinSessionResponse] =
    DeriveJsonEncoder.gen[JoinSessionResponse]

  given zio.json.JsonDecoder[JoinSessionResponse] =
    DeriveJsonDecoder.gen[JoinSessionResponse]
}

case class LeaveSessionResponse(message: String)
object LeaveSessionResponse {
  given zio.json.JsonEncoder[LeaveSessionResponse] =
    DeriveJsonEncoder.gen[LeaveSessionResponse]

  given zio.json.JsonDecoder[LeaveSessionResponse] =
    DeriveJsonDecoder.gen[LeaveSessionResponse]
}

case class FailedResponse(failure: String)
object FailedResponse {
  given zio.json.JsonEncoder[FailedResponse] =
    DeriveJsonEncoder.gen[FailedResponse]

  given zio.json.JsonDecoder[FailedResponse] =
    DeriveJsonDecoder.gen[FailedResponse]
}

case class SessionListResponse(success: List[String])
object SessionListResponse {
  given JsonEncoder[SessionListResponse] =
    DeriveJsonEncoder.gen[SessionListResponse]

  given JsonDecoder[SessionListResponse] =
    DeriveJsonDecoder.gen[SessionListResponse]
}
