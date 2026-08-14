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

map StringMap {
    key: String
    value: String
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

@trait(
    selector: """
        operation:test(
            operation
            -[output]-> structure
            > member[id|member = 'location'][trait|smithy.api#httpHeader = 'Location']
            > string
        )
        """
)
@range(min: 300, max: 399)
integer redirect


enum AccountRole {
  NONE = "none",
  STATS = "stats",
  MOD = "mod",
  ADMIN = "admin"
}

@trait(selector: ":is(service, operation)")
structure reqRole {
    @required
    role: AccountRole
}
