$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@error("client")
@httpError(401)
structure NotAuthenticated {
    reason: String
    message: String
}

@error("client")
@httpError(404)
structure NotFound {}

@error("client")
@httpError(403)
structure InsufficientPermissions {}
