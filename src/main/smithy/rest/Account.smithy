$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
@httpBearerAuth
service AccountService {
  version: "1.0.0"
  operations: [GetSelf, CreateAccount, UpdateAccount]
}

@http(method: "GET", uri: "/api/accounts/@me")
@readonly
operation GetSelf {
  output: Account
}

@http(method: "POST", uri: "/api/accounts")
operation CreateAccount {
  input := {
    @required
    username: String

    @required
    password: String

    @required
    role: AccountRole

    @required
    name: String
  }

  output: Account
}

@http(method: "PATCH", uri: "/api/accounts/{userId}")
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
  id: Integer

  @required
  name: String

  @required
  role: AccountRole

  @required
  @timestampFormat("date-time")
  created_at: Timestamp

  @required
  @timestampFormat("date-time")
  updated_at: Timestamp
}

enum AccountRole {
  NONE = "none",
  STATS = "stats",
  MOD = "mod",
  ADMIN = "admin"
}
