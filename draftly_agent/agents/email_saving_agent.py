"""
Agent 1 — EmailSavingAgent

Subscribes to TOPIC_SAVING_EMAIL.
For each message:
  1. Embeds the email body
  2. Saves the embedding + metadata to PostgreSQL
  3. Rebuilds the generic writing-style profile for (user_id, relation)

Expected message schema:
{
  "user_id":    "u_123",
  "thread_id":  "thread_abc",
  "relation":   "Existing Customer",
  "from":       "alice@example.com",
  "subject":    "Re: Invoice #42",
  "body":       "Hi, I just wanted to follow up …",
  "metadata":   {}          # optional
}
"""
from graphs.saving_graph import build_saving_graph, SavingState
from services.pubsub_service import subscribe_in_thread
from config import config


class EmailSavingAgent:
    def __init__(self):
        self._graph = build_saving_graph()

    def _handle(self, payload: dict) -> None:
        # Java publishes SavingEmailKafkaMessage (camelCase). Support both that and
        # the snake_case names used in local tests.
        user_id    = payload.get("gmailAccountEmail") or payload.get("user_id", "")
        relation   = payload.get("relationName") or payload.get("relation", "Unknown")
        thread_id  = payload.get("threadId") or payload.get("thread_id", "")
        from_email = payload.get("senderEmail") or payload.get("from", "")
        body       = (payload.get("body", "") or "").strip()

        if not body:
            print(f"[SavingAgent] Skipping empty body (thread={thread_id})")
            return

        print(f"[SavingAgent] Processing thread={thread_id} user={user_id} relation={relation}")

        state: SavingState = {
            "user_id":    user_id,
            "relation":   relation,
            "thread_id":  thread_id,
            "from_email": from_email,
            "subject":    payload.get("subject", ""),
            "body":       body,
            "metadata":   payload.get("metadata", {}),
            "embedding":  [],
            "errors":     [],
        }

        result = self._graph.invoke(state)

        if result.get("errors"):
            print(f"[SavingAgent] Errors: {result['errors']}")
        else:
            print(f"[SavingAgent] Saved embedding for thread={thread_id}")

    def start(self) -> None:
        print(f"[SavingAgent] Starting on topic '{config.TOPIC_SAVING_EMAIL}' …")
        subscribe_in_thread(config.TOPIC_SAVING_EMAIL, self._handle)
