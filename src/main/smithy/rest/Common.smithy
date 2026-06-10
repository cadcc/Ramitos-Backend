$version: "2.0"

namespace cl.cadcc.ramitos.schema

use smithy4s.meta#vector

@length(min: 1)
@vector
list NonEmptyStringList {
    member: String
}

@vector
list IntegerList {
    member: Integer
}

@vector
list StringList {
    member: String
}

enum Ordering {
    ASCENDING = "asc"
    DESCENDING = "desc"
}

@streaming
blob ByteStream

@trait(selector: ":is(service, operation)")
structure preferGzip {}

@trait(selector: ":is(service, operation)")
structure conditionalByDate {}
