$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
service AnonymousReviewService {
  version: "1.0.0"
  operations: [ListCourseReviews]
}

@http(method: "GET", uri: "/api/courses/{courseCode}/reviews")
@readonly
operation ListCourseReviews {
  input := {
    @required
    @httpLabel
    courseCode: String

    @httpQuery("limit")
    @range(min: 1, max: 50)
    @default(50)
    limit: Long

    @httpQuery("after")
    after: Integer
  }
  output := {
    @httpPayload
    @required
    content: AnonymousReviews
  }
  errors: [NotFound]
}

list AnonymousReviews {
  member: AnonymousReview
}

structure AnonymousReview {
  @required
  id: Long

  @required
  comments: String

  @required
  stats: ReviewStats

  @required
  tags: ReviewTags

  @required
  @timestampFormat("date-time")
  created_at: Timestamp
}
