$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
@internal
service RawByteStreamsService {
    operations: [RawGetCoursesStaticData]
}


@http(method: "GET", uri: "/api/courses.json")
@readonly
@preferGzip
@conditionalByDate
operation RawGetCoursesStaticData {
    input := {
    }

    output := {
        @httpPayload
        @required
        content : ByteStream
    }
}
