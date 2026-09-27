$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson
use smithy.api#NonEmptyStringList

@simpleRestJson
@httpBearerAuth
service CourseService {
  version: "1.2.0"
  operations: [ListCourses, GetCoursesStaticData, GetCourse]
}

@http(method: "GET", uri: "/api/courses")
@readonly
@optionalAuth
operation ListCourses {
  input := {
    @httpQuery("limit")
    @range(min: 1, max: 50)
    @default(50)
    limit: Long

    @httpQuery("codes")
    @length(min: 1, max: 50)
    codes: CourseListCodes

    @httpQuery("enrolled")
    enrolled: Boolean

    @httpQuery("reviewable")
    reviewable: Boolean

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
@optionalAuth
operation GetCourse {
  input := {
    @required
    @httpLabel
    courseId: String
  }
  output: Course
}

@http(method: "GET", uri: "/api/courses.json")
@readonly
@auth([])
operation GetCoursesStaticData {
  input := {}

  output := {
    @httpPayload
    @required
    content: CoursesStaticDataContainer
  }
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
  reviewCount: Integer

  @required
  stats: CourseStats

  @required
  tag_stats: CourseTagStats

  enrolled: Boolean
  reviewable: Boolean
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

list CourseListCodes {
  member: String
}

structure CoursesStaticDataContainer {
  @required
  count: Integer

  @required
  courses: CoursesStaticData

  @required
  categories: CategoryMap
}

list CoursesStaticData {
  member: CourseStaticData
}

list CategoryMap {
  member: String
}

structure CourseStaticData {
  @required
  code: String

  @required
  name: String

  categories: IntegerList
  mallas: IntegerList
}
