"""
Agent 2 — DraftEmailAgent

Subscribes to TOPIC_DRAFT_EMAIL.
For each message:
  1. Decides if a reply draft is needed
  2. Checks if the body has enough context to reply
     - If not → fetches up to 5 thread emails via gRPC and enriches the body
  3. Checks embedding similarity against saved emails for (user_id, relation)
     - similarity ≥ threshold → draft mirroring the matched email's style
     - similarity <  threshold → draft using the generic writing-style profile
  4. If no draft needed → summarise
  5. Publishes result to TOPIC_OUTPUT and persists to DB

Expected message schema:
{
  "user_id":   "u_123",
  "thread_id": "thread_abc",
  "relation":  "Existing Customer",
  "from":      "alice@acme.com",
  "subject":   "Re: Invoice #42",
  "body":      "Hi, could you …",
  "context":   "Keep it brief."   <- optional
}
"""
from graphs.draft_graph import build_draft_graph, DraftState
from services.pubsub_service import subscribe_in_thread
from config import config


class DraftEmailAgent:
    def __init__(self):
        self._graph = build_draft_graph()

    def _handle(self, payload: dict) -> None:
        # Java publishes DraftEmailKafkaMessage (camelCase). Support both that and
        # the snake_case names used in local tests.
        user_id    = payload.get("gmailAccountEmail") or payload.get("user_id", "")
        relation   = payload.get("relationName") or payload.get("relation", "Unknown")
        thread_id  = payload.get("threadId") or payload.get("thread_id", "")
        from_email = payload.get("senderEmail") or payload.get("from", "")
        body       = (payload.get("emailBody") or payload.get("body", "") or "").strip()
        context    = payload.get("relationContext") or payload.get("context", "")
        # Present only when the email is re-submitted after the user answered a decision request
        user_decision = payload.get("userDecision") or payload.get("user_decision", "")

        if not body:
            print(f"[DraftAgent] Skipped — empty body (thread={thread_id})")
            return

        print(f"[DraftAgent] Processing  thread={thread_id}  user={user_id}  relation={relation}"
              + (f"  decision={user_decision}" if user_decision else ""))

        state: DraftState = {
            "user_id":           user_id,
            "relation":          relation,
            "thread_id":         thread_id,
            "from_email":        from_email,
            "subject":           payload.get("subject", ""),
            "body":              body,
            "context":           context,
            "needs_user_decision": False,
            "decision_question":   "",
            "decision_options":    [],
            "user_decision":       user_decision,
            "needs_draft":       False,
            "body_has_context":  True,
            "thread_emails":     [],
            "enriched_body":     body,
            "query_embedding":   [],
            "best_match_body":   None,
            "best_similarity":   0.0,
            "generic_style":     None,
            "draft_content":     "",
            "summary":           "",
            "errors":            [],
        }

        result = self._graph.invoke(state)

        if result.get("errors"):
            print(f"[DraftAgent] Errors: {result['errors']}")
        else:
            msg_type   = "draft" if result.get("needs_draft") else "summary"
            sim        = result.get("best_similarity", 0.0)
            used_grpc  = bool(result.get("thread_emails"))
            print(
                f"[DraftAgent] Published {msg_type} for thread={thread_id}"
                + (f"  similarity={sim:.1f}%" if result.get("needs_draft") else "")
                + ("  [gRPC context used]" if used_grpc else "")
            )

    def start(self):
        print(f"[DraftAgent] Listening on topic '{config.TOPIC_DRAFT_EMAIL}' …")
        subscribe_in_thread(config.TOPIC_DRAFT_EMAIL, self._handle)
