$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@error("client")
@httpError(401)
structure NotAuthorized {
    reason: String
    message: String
}
