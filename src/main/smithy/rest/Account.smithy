$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
@httpBearerAuth
service AccountService {
  version: "1.0.0"
  operations: [GetSelf, CreateAccount, UpdateAccount]
}

@http(method: "GET", uri: "/api/users/@me")
@readonly
operation GetSelf {
  output: Account
}

@http(method: "POST", uri: "/api/users")
operation CreateAccount {
  input := {
    @required
    username: String

    @required
    password: String

    @required
    name: String
  }

  output: Account
}

@http(method: "PATCH", uri: "/api/users/{userId}")
@idempotent
operation UpdateAccount {
  input := {
    @required
    @httpLabel
    userId: String
    
    name: String
  }
  output: Account
}

structure Account {
  @required
  id: Long

  @required
  name: String

  @required
  created_at: Timestamp

  @required
  updated_at: Timestamp
}
