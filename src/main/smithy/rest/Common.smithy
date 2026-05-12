$version: "2.0"

namespace cl.cadcc.ramitos.schema

@length(min: 1)
list NonEmptyStringList {
    member: String
}

enum Ordering {
    ASCENDING = "asc"
    DESCENDING = "desc"
}
