$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
service AuthenticationService {
  version: "1.0.0"
  operations: [PasswordLogin, DccLogin]
}

@http(method: "POST", uri: "/api/workflow/login/pass", code: 200)
operation PasswordLogin {
  input := {
    @required
    username: String

    @required
    password: String
  } 
  output: SessionTokens
}

@http(method: "GET", uri: "/api/workflow/login/dcc", code: 200)
operation DccLogin {
  input := {
    @required
    @httpQuery("username")
    username: String

    @required
    @httpQuery("secret")
    secret: String
  }
  output: SessionTokens
}

structure PasswordCredentials {
  @required
  name: String

  @required
  password: String
}

structure DccCredentials {
  @required
  @httpQuery("username")
  username: String

  @required
  @httpQuery("secret")
  secret: String
}

structure SessionTokens {
  @required
  message: String
}
