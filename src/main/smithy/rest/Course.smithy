$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

@simpleRestJson
service CourseService {
  version: "1.0.0"
  operations: [ListCourses, GetCourse]
}

@http(method: "GET", uri: "/api/courses")
@readonly
operation ListCourses {
  input := {
    @httpQuery("limit")
    @range(min: 1) // removed max. May be dangerous! discuss consequences later.
    @default(50)
    limit: Long

    @httpQuery("after")
    after: String
  }
  output := {
    @httpPayload
    @required
    content: Courses
  }
}

@http(method: "GET", uri: "/api/courses/{courseId}")
@readonly
operation GetCourse {
  input := {
    @required
    @httpLabel
    courseId: String
  }
  output: Course
}

list Courses {
  member: Course
}

structure Course {
  @required
  id: String

  @required
  name: String

  @required
  stats: CourseStats

  @required
  tag_stats: CourseTagStats
}

structure CourseStats {
  docencia: CourseStat
  vibes: CourseStat
  relevancia: CourseStat
  carga: CourseStat
  dificultad: CourseStat
}

structure CourseStat {
  @required
  value: Float
}

map CourseTagStats {
  key: String
  value: CourseStat
}
