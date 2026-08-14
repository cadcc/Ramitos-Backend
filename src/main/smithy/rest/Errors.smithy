$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@error("client")
@httpError(400)
structure InvalidCredentials {

}

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

@error("client")
@httpError(422)
structure DuplicatedEntity {
    @required
    reason: String = "DuplicatedEntity"

    @required
    conflicting_fields: NonEmptyStringList
}

@error("client")
@httpError(400)
structure StatisticallyImpossible {
    @required
    reason: String
}

@error("client")
@httpError(400)
structure WorkflowTrackerCookieMissing {
    @required
    cookieName: String
}

@error("client")
@httpError(400)
structure RequestReplayed {
    @required
    reason: String = "The server refuses to answer because the request was replayed."
}

@error("client")
@httpError(400)
structure WorkflowTimeout {
    @required
    reason: String = "The client took too long to finish the workflow. May retry from the start."

    /// use this Uri to start the workflow again
    @required
    retry: String
}
