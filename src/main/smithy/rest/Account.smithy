$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
@httpBearerAuth
service AccountService {
  version: "1.0.0"
  operations: [GetSelf, CreateAccount, UpdateAccount]
  errors: [NotAuthenticated, InsufficientPermissions]
}

@http(method: "GET", uri: "/api/accounts/@me")
@readonly
operation GetSelf {
  output: Account
}

@http(method: "POST", uri: "/api/accounts")
@reqRole(role: "admin")
operation CreateAccount {
  input := {
    @required
    username: String

    @required
    password: String

    /// Must be a MUFASA compatible identifier. If missing, this user skips MUFASA checks
    mufasaId: String

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

  errors: [NotFound]
}

structure Account {
  @required
  id: Integer

  @required
  name: String

  /// Usually it's a RUT.
  mufasaId: String

  @required
  role: AccountRole

  @required
  @timestampFormat("date-time")
  created_at: Timestamp

  @required
  @timestampFormat("date-time")
  updated_at: Timestamp
}
