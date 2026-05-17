$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
@httpBearerAuth
service ReviewService {
    version: "1.0.0"
    operations: [GetReview, CreateReview, ListReviews]
}

@http(method: "GET", uri: "/api/reviews/{reviewId}")
@readonly
operation GetReview {
    input := {
        @required
        @httpLabel
        reviewId: Integer
    }
    output : Review
}

@http(method: "POST", uri: "/api/reviews")
operation CreateReview {
    input := {
        @required
        course_code: String

        comments: String

        @required
        stats: ReviewStats

        @required
        tags: ReviewTags
    }
    output : Review
    errors: [DuplicatedEntity]
}

@http(method: "GET", uri: "/api/reviews")
@readonly
operation ListReviews {
    input := {
        @httpQuery("course_id")
        courseId: String

        @httpQuery("account_id")
        accountId: Integer

        @httpQuery("limit")
        @range(min: 1, max: 50)
        @default(50)
        limit: Long

        @httpQuery("after")
        after: Integer

        @httpQuery("created_order")
        @default("desc")
        createdOrder: Ordering
    }
    output := {
        @httpPayload
        @required
        content: Reviews
    }
}

list Reviews {
    member: Review
}

structure Review {
    @required
    id: Integer

    @required
    account_id: Integer

    @required
    course_code: String

    comments: String

    @required
    stats: ReviewStats

    @required
    tags: ReviewTags

    @required
    @timestampFormat("date-time")
    created_at: Timestamp
}

structure ReviewStats {
    @range(min: 1, max: 5)
    docencia: Byte

    @range(min: 1, max: 5)
    vibes: Byte

    @range(min: 1, max: 5)
    relevancia: Byte

    @range(min: 1, max: 5)
    carga: Byte

    @range(min: 1, max: 5)
    dificultad: Byte
}

list ReviewTags {
    member: String
}
