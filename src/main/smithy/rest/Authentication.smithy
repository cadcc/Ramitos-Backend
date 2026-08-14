$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
service AuthenticationService {
  version: "1.0.0"
  operations: [PasswordLogin, DccLoginStart, DccLoginCallback, DccLoginExchangeTokens]
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
  errors : [InvalidCredentials]
}

@http(method: "GET", uri: "/api/workflow/login/dcc/start", code: 200)
@redirect(302)
operation DccLoginStart {
  input := {
    @httpQuery("redirect")
    redirect: String
  }
  output := {
    @httpHeader("Location")
    @required
    location: String

    @httpHeader("Set-Cookie")
    @required
    cookie: String
  }
  errors : [CallbackRejected, StatisticallyImpossible]
}

@http(method: "GET", uri: "/api/workflow/login/dcc", code: 200)
@redirect(302)
operation DccLoginCallback {
  input := {
    @httpQueryParams
    @required
    params: StringMap

    @httpHeader("Cookie")
    @required
    cookies: String
  }
  output := {
    @httpHeader("Location")
    @required
    location: String
  }
  errors : [WorkflowTrackerCookieMissing, WorkflowTimeout, RequestReplayed, CallbackRejected]
}

@http(method: "POST", uri: "/api/workflow/login/dcc/finish", code: 200)
operation DccLoginExchangeTokens {
  input := {
    @required
    secret: String

    @required
    @httpHeader("Cookie")
    cookies: String
  }
  output : SessionTokens
  errors : [RequestReplayed, WorkflowTimeout]
}

structure SessionTokens {
  @required
  accessToken: String
}

@error("client")
@httpError(400)
structure CallbackRejected {
  message: String
}
