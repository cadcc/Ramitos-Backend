$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
service WoofService {
    version: "1.0.0"
    operations: [Woof]
}

@http(method: "GET", uri: "/api/woof")
@readonly
operation Woof {
    output := {
        @httpPayload
        content: String = "Woof Woof!"
    }
}
