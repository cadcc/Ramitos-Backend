$version: "2.0"

namespace cl.cadcc.ramitos.schema

use alloy#simpleRestJson

/// Moderation actions. Every operation requires role MOD or higher and
/// every state-changing action is recorded in the moderation audit log.
@simpleRestJson
@httpBearerAuth
@reqRole(role: "mod")
service ModerationService {
    version: "1.0.0"
    operations: [HideReviewComment, UnhideReviewComment, ListModerationActions]
    errors: [NotAuthenticated, InsufficientPermissions]
}

/// Hides the comment of a review from public listings. The author still sees it, flagged as hidden.
@http(method: "POST", uri: "/api/moderation/reviews/{reviewId}/hide")
operation HideReviewComment {
    input := with [ModerationRequest] {
        @required
        @httpLabel
        reviewId: Integer
    }
    output: ModerationAction
    errors: [NotFound, InvalidModerationState]
}

/// Restores a previously hidden review comment.
@http(method: "POST", uri: "/api/moderation/reviews/{reviewId}/unhide")
operation UnhideReviewComment {
    input := with [ModerationRequest] {
        @required
        @httpLabel
        reviewId: Integer
    }
    output: ModerationAction
    errors: [NotFound, InvalidModerationState]
}

/// Lists audit log entries, newest first.
@http(method: "GET", uri: "/api/moderation/actions")
@readonly
operation ListModerationActions {
    input := {
        @httpQuery("review_id")
        reviewId: Integer

        @httpQuery("actor_id")
        actorId: Integer

        @httpQuery("action")
        action: ModerationActionType

        @httpQuery("limit")
        @range(min: 1, max: 50)
        @default(50)
        limit: Long

        /// Return entries with an id lower than this one.
        @httpQuery("before")
        before: Integer
    }
    output := {
        @httpPayload
        @required
        content: ModerationActions
    }
}

@mixin
structure ModerationRequest {
    @required
    @length(min: 1, max: 1000)
    reason: String
}

enum ModerationActionType {
    REVIEW_COMMENT_HIDE = "review_comment_hide"
    REVIEW_COMMENT_UNHIDE = "review_comment_unhide"
}

/// What a moderation action was applied to. One member per target kind.
union ModerationTarget {
    review: Integer
}

list ModerationActions {
    member: ModerationAction
}

structure ModerationAction {
    @required
    id: Integer

    @required
    actor_account_id: Integer

    @required
    action: ModerationActionType

    @required
    target: ModerationTarget

    @required
    reason: String

    /// Action-specific data, e.g. a snapshot of the target's previous state.
    @required
    details: Document

    @required
    @timestampFormat("date-time")
    created_at: Timestamp
}
