package org.torii_gate.db

enum DbErrors:
  case UserNotFound(message: String)

  def message: String