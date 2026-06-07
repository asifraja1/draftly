"""
LangGraph for DraftEmailAgent — with Human-in-the-Loop (HITL)

Flow:
  check_if_draft_needed
    ├─ no  → summarise_email → publish_output → END
    └─ yes → check_body_context
                 ├─ insufficient → fetch_thread_emails (gRPC)
                 └─ sufficient  ─┐
                                 ▼
                      check_needs_user_decision
                         ├─ no  → check_similarity
                         └─ yes → ask_user_and_wait   ← interrupt() PAUSES HERE
                                       │
                                  (user answers via Kafka)
                                       │
                                  resume_with_answer   ← graph RESUMES HERE
                                       ▼
                              check_similarity
                                ├─ ≥ threshold → draft_with_embedding_style
                                └─ < threshold → fetch_generic_style → draft_with_generic_style
                                                         ▼
                                                  publish_output → END
"""
import json
import operator
from typing import TypedDict, Annotated, List, Optional

from langgraph.graph import StateGraph, END
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage

from services.embedding_service import embed
from services.vector_store import search_similar
from services.writing_style_service import get_writing_style
from services.grpc_client import fetch_thread_emails
from services.pubsub_service import publish
from database.connection import db_cursor
from config import config

_llm = ChatOpenAI(model=config.LLM_MODEL, temperature=0.3, api_key=config.OPENAI_API_KEY)


# ── State ──────────────────────────────────────────────────────────────────────

class DraftState(TypedDict):
    # inputs
    user_id:    str
    relation:   str
    thread_id:  str
    from_email: str
    subject:    str
    body:       str
    context:    str

    # HITL fields
    needs_user_decision: bool
    decision_question:   str          # question shown to user
    decision_options:    List[str]    # choices e.g. ["Accept", "Decline"]
    user_decision:       str          # filled after user answers

    # intermediate
    needs_draft:         bool
    body_has_context:    bool
    thread_emails:       List[dict]
    enriched_body:       str
    query_embedding:     List[float]
    best_match_body:     Optional[str]
    best_similarity:     float
    generic_style:       Optional[dict]

    # outputs
    draft_content: str
    summary:       str

    errors: Annotated[List[str], operator.add]


# ── Node 1: decide draft vs summary ───────────────────────────────────────────

def check_if_draft_needed_node(state: DraftState) -> dict:
    prompt = f"""Decide whether this email requires a personal reply or just a summary.

Email:
From: {state.get('from_email', '')}
Subject: {state.get('subject', '')}
Body: {state['body']}

Rules:
- Questions, requests, complaints, inquiries → needs_draft = true
- Newsletters, notifications, receipts, marketing → needs_draft = false

Return ONLY valid JSON:
{{"needs_draft": true | false, "reason": "one sentence"}}"""
    try:
        res = _llm.invoke([HumanMessage(content=prompt)])
        parsed = json.loads(res.content.strip().replace("```json","").replace("```",""))
        return {"needs_draft": bool(parsed.get("needs_draft", True))}
    except Exception as exc:
        return {"needs_draft": True, "errors": [f"check_if_draft_needed: {exc}"]}


# ── Node 2: check body context ────────────────────────────────────────────────

def check_body_context_node(state: DraftState) -> dict:
    prompt = f"""Does this email body have enough context to write a meaningful reply alone?

Body: \"\"\"{state['body']}\"\"\"

Return ONLY valid JSON:
{{"sufficient": true | false, "reason": "one sentence"}}"""
    try:
        res = _llm.invoke([HumanMessage(content=prompt)])
        parsed = json.loads(res.content.strip().replace("```json","").replace("```",""))
        return {
            "body_has_context": bool(parsed.get("sufficient", True)),
            "enriched_body":    state["body"],
        }
    except Exception as exc:
        return {"body_has_context": True, "enriched_body": state["body"],
                "errors": [f"check_body_context: {exc}"]}


# ── Node 3: fetch thread emails via gRPC ──────────────────────────────────────

def fetch_thread_emails_node(state: DraftState) -> dict:
    try:
        emails = fetch_thread_emails(state["thread_id"], state["user_id"], limit=5)
        if not emails:
            return {"thread_emails": [], "enriched_body": state["body"]}

        history = "\n\n---\n\n".join(
            f"[{e['timestamp']}] From: {e['from']}\n{e['body']}"
            for e in reversed(emails)
        )
        enriched = (
            f"=== THREAD HISTORY ===\n{history}\n\n"
            f"=== LATEST EMAIL ===\n{state['body']}"
        )
        return {"thread_emails": emails, "enriched_body": enriched}
    except Exception as exc:
        return {"thread_emails": [], "enriched_body": state["body"],
                "errors": [f"fetch_thread_emails: {exc}"]}


# ── Node 4: detect if user decision is needed ─────────────────────────────────

def check_needs_user_decision_node(state: DraftState) -> dict:
    """
    Detects emails where the reply depends entirely on a personal decision
    the agent cannot make — e.g. job offers, event invitations, partnership
    proposals, salary negotiations.
    """
    body = state.get("enriched_body") or state["body"]
    prompt = f"""You analyse emails to detect if replying requires a personal YES/NO decision
from the recipient that an AI cannot assume on their behalf.

Examples that NEED user decision:
- Job offer / interview invite  → "Should I accept?"
- Event invitation              → "Will you attend?"
- Partnership / deal proposal   → "Are you interested?"
- Salary negotiation            → "Do you accept this offer?"

Examples that do NOT need user decision:
- Request for information       → agent can answer from context
- Complaint                     → agent can apologise and respond
- Invoice / payment question    → agent can respond factually

Email:
From: {state.get('from_email','')}
Subject: {state.get('subject','')}
Body:
\"\"\"
{body}
\"\"\"

Return ONLY valid JSON:
{{
  "needs_decision": true | false,
  "question": "The specific question to ask the user (empty string if false)",
  "options": ["Option A", "Option B", "Option C"]
}}"""
    try:
        res = _llm.invoke([HumanMessage(content=prompt)])
        parsed = json.loads(res.content.strip().replace("```json","").replace("```",""))
        return {
            "needs_user_decision": bool(parsed.get("needs_decision", False)),
            "decision_question":   parsed.get("question", ""),
            "decision_options":    parsed.get("options", ["Yes", "No"]),
        }
    except Exception as exc:
        return {
            "needs_user_decision": False,
            "decision_question":   "",
            "decision_options":    [],
            "errors": [f"check_needs_user_decision: {exc}"],
        }


# ── Node 5: request a decision from the user (no pause/interrupt) ──────────────

def request_user_decision_node(state: DraftState) -> dict:
    """
    Instead of pausing the graph (which needs a checkpointer + resume plumbing),
    we publish a NEEDS_INPUT message to the output topic and END this run.

    The Java app saves it as a "needs your input" item; the frontend shows the
    question + options. When the user picks one, Java re-publishes the SAME
    email to draft_email WITH `userDecision` set — the graph then runs again,
    skips this node, and produces the final draft using that decision.
    """
    payload = {
        "type":              "NEEDS_INPUT",
        "threadId":          state["thread_id"],
        "senderEmail":       state.get("from_email", ""),
        "gmailAccountEmail": state["user_id"],
        "content":           "",                       # no draft yet
        "originalEmailBody": state["body"],
        "question":          state.get("decision_question", ""),
        "options":           json.dumps(state.get("decision_options", [])),
    }
    try:
        publish(config.TOPIC_OUTPUT, payload)
        print(f"[HITL] Requested user decision for thread={state['thread_id']}: "
              f"{state.get('decision_question','')}")
    except Exception as exc:
        return {"errors": [f"request_user_decision: {exc}"]}
    return {}


# ── Node 6: similarity search ──────────────────────────────────────────────────

def check_similarity_node(state: DraftState) -> dict:
    body_to_embed = state.get("enriched_body") or state["body"]
    try:
        query_vec = embed(body_to_embed)
        hits = search_similar(
            query_embedding=query_vec,
            user_id=state["user_id"],
            relation=state["relation"],
            top_k=1,
        )
        if hits:
            return {
                "query_embedding": query_vec,
                "best_match_body": hits[0]["body"],
                "best_similarity": hits[0]["similarity"],
            }
        return {"query_embedding": query_vec, "best_match_body": None, "best_similarity": 0.0}
    except Exception as exc:
        return {"query_embedding": [], "best_match_body": None, "best_similarity": 0.0,
                "errors": [f"check_similarity: {exc}"]}


# ── Node 7a: draft using matched email style ──────────────────────────────────

def draft_with_embedding_style_node(state: DraftState) -> dict:
    decision_line = (
        f"\nIMPORTANT: The user has decided: {state['user_decision']}\n"
        if state.get("user_decision") else ""
    )
    prompt = f"""You are a professional email ghostwriter. Mirror the style of the REFERENCE EMAIL exactly.

REFERENCE EMAIL (style to match):
\"\"\"{state.get('best_match_body', '')}\"\"\"

EMAIL TO REPLY TO:
\"\"\"{state.get('enriched_body') or state['body']}\"\"\"
{decision_line}
{f"Extra instructions: {state['context']}" if state.get('context') else ''}

Write ONLY the reply body."""
    try:
        res = _llm.invoke([HumanMessage(content=prompt)])
        return {"draft_content": res.content.strip()}
    except Exception as exc:
        return {"draft_content": "", "errors": [f"draft_with_embedding_style: {exc}"]}


# ── Node 7b: fetch generic style ──────────────────────────────────────────────

def fetch_generic_style_node(state: DraftState) -> dict:
    return {"generic_style": get_writing_style(state["user_id"], state["relation"])}


# ── Node 7c: draft using generic style ───────────────────────────────────────

def draft_with_generic_style_node(state: DraftState) -> dict:
    style = state.get("generic_style") or {}
    decision_line = (
        f"\nIMPORTANT: The user has decided: {state['user_decision']}\n"
        if state.get("user_decision") else ""
    )
    style_block = f"""
Tone: {style.get('tone','professional')} | Formality: {style.get('formality','medium')}
Avg sentence length: ~{style.get('avg_sentence_len',15)} words
Style notes: {style.get('style_description','Clear and concise.')}
Common phrases: {json.dumps(style.get('common_phrases',[]))}""".strip()

    prompt = f"""You are a professional email ghostwriter. Follow the style guide below.

STYLE GUIDE:
{style_block}

EMAIL TO REPLY TO:
\"\"\"{state.get('enriched_body') or state['body']}\"\"\"
{decision_line}
{f"Extra instructions: {state['context']}" if state.get('context') else ''}

Write ONLY the reply body."""
    try:
        res = _llm.invoke([HumanMessage(content=prompt)])
        return {"draft_content": res.content.strip()}
    except Exception as exc:
        return {"draft_content": "", "errors": [f"draft_with_generic_style: {exc}"]}


# ── Node 8: summarise (no-draft path) ─────────────────────────────────────────

def summarise_email_node(state: DraftState) -> dict:
    prompt = f"""Summarise this email in 2-3 sentences. Include sender, key point, action items.

From: {state.get('from_email','')} | Subject: {state.get('subject','')}
Body: {state['body']}

Return ONLY plain-text summary."""
    try:
        res = _llm.invoke([HumanMessage(content=prompt)])
        return {"summary": res.content.strip()}
    except Exception as exc:
        return {"summary": "", "errors": [f"summarise_email: {exc}"]}


# ── Node 9: publish output ────────────────────────────────────────────────────

def publish_output_node(state: DraftState) -> dict:
    is_draft = state.get("needs_draft", False)
    content  = state.get("draft_content") if is_draft else state.get("summary")

    # Must match Java's OutputKafkaMessage DTO (camelCase). Java compares the
    # type with equalsIgnoreCase against "DRAFT"/"SUMMARY".
    payload = {
        "type":              "DRAFT" if is_draft else "SUMMARY",
        "threadId":          state["thread_id"],
        "senderEmail":       state.get("from_email", ""),
        "gmailAccountEmail": state["user_id"],
        "content":           content,
        "originalEmailBody": state["body"],
    }
    try:
        publish(config.TOPIC_OUTPUT, payload)
        with db_cursor() as cur:
            cur.execute(
                """
                INSERT INTO output_messages
                    (user_id, thread_id, message_type, content,
                     original_email, similarity, metadata)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    state["user_id"], state["thread_id"],
                    payload["type"], content,
                    state["body"], state.get("best_similarity"),
                    json.dumps({
                        "used_grpc":     bool(state.get("thread_emails")),
                        "user_decision": state.get("user_decision"),
                    }),
                ),
            )
        return {}
    except Exception as exc:
        return {"errors": [f"publish_output: {exc}"]}


# ── Routing ────────────────────────────────────────────────────────────────────

def route_after_draft_check(state: DraftState) -> str:
    return "check_body_context" if state.get("needs_draft") else "summarise_email"

def route_after_context_check(state: DraftState) -> str:
    return "check_similarity" if state.get("body_has_context") else "fetch_thread_emails"

def route_after_decision_check(state: DraftState) -> str:
    # If the user already supplied a decision (the email was re-submitted after
    # they answered), skip straight to drafting.
    if state.get("user_decision"):
        return "check_similarity"
    # Otherwise, if a decision is needed, ask the user and end this run.
    if state.get("needs_user_decision"):
        return "request_user_decision"
    return "check_similarity"

def route_after_similarity(state: DraftState) -> str:
    if state.get("best_match_body") and state.get("best_similarity", 0) >= config.SIMILARITY_THRESHOLD:
        return "draft_with_embedding_style"
    return "fetch_generic_style"


# ── Graph ──────────────────────────────────────────────────────────────────────

def build_draft_graph(checkpointer=None):
    """
    HITL is handled WITHOUT interrupt()/checkpointer: when a decision is needed,
    the graph publishes a NEEDS_INPUT message and ends. After the user answers,
    the email is re-submitted with `userDecision` set and the graph runs again,
    skipping the decision request.
    """
    g = StateGraph(DraftState)

    g.add_node("check_if_draft_needed",      check_if_draft_needed_node)
    g.add_node("check_body_context",         check_body_context_node)
    g.add_node("fetch_thread_emails",        fetch_thread_emails_node)
    g.add_node("check_needs_user_decision",  check_needs_user_decision_node)
    g.add_node("request_user_decision",      request_user_decision_node)
    g.add_node("check_similarity",           check_similarity_node)
    g.add_node("draft_with_embedding_style", draft_with_embedding_style_node)
    g.add_node("fetch_generic_style",        fetch_generic_style_node)
    g.add_node("draft_with_generic_style",   draft_with_generic_style_node)
    g.add_node("summarise_email",            summarise_email_node)
    g.add_node("publish_output",             publish_output_node)

    g.set_entry_point("check_if_draft_needed")

    g.add_conditional_edges("check_if_draft_needed", route_after_draft_check, {
        "check_body_context": "check_body_context",
        "summarise_email":    "summarise_email",
    })
    g.add_conditional_edges("check_body_context", route_after_context_check, {
        "check_similarity":    "check_needs_user_decision",
        "fetch_thread_emails": "fetch_thread_emails",
    })
    g.add_edge("fetch_thread_emails", "check_needs_user_decision")

    g.add_conditional_edges("check_needs_user_decision", route_after_decision_check, {
        "request_user_decision": "request_user_decision",
        "check_similarity":      "check_similarity",
    })
    g.add_edge("request_user_decision", END)   # ends until the user answers

    g.add_conditional_edges("check_similarity", route_after_similarity, {
        "draft_with_embedding_style": "draft_with_embedding_style",
        "fetch_generic_style":        "fetch_generic_style",
    })
    g.add_edge("draft_with_embedding_style", "publish_output")
    g.add_edge("fetch_generic_style",        "draft_with_generic_style")
    g.add_edge("draft_with_generic_style",   "publish_output")
    g.add_edge("summarise_email",            "publish_output")
    g.add_edge("publish_output",             END)

    return g.compile(checkpointer=checkpointer)
