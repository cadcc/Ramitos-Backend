$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
@httpBearerAuth
service AnonymousReviewService {
  version: "1.0.0"
  operations: [ListCourseReviews]
}

@http(method: "GET", uri: "/api/courses/{courseId}/reviews")
@readonly
operation ListCourseReviews {
  input := {
    @required
    @httpLabel
    courseId: String

    @httpQuery("limit")
    limit: Long

    @httpQuery("after")
    after: Integer
  }
  output := {
    @httpPayload
    @required
    content: AnonymousReviews
  }
}

list AnonymousReviews {
  member: AnonymousReview
}

structure AnonymousReview {
  @required
  id: Long

  @required
  @length(min: 20, max: 1000)
  comment: String

  @required
  stats: ReviewStats

  @required
  tags: ReviewStats

  created_at: Timestamp
}
