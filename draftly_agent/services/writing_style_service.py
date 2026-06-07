"""
Builds and retrieves the generic writing style for a (user_id, relation) pair.

Email bodies are fetched from Chroma (the vector store), not Postgres.
The resulting style profile is upserted into the writing_styles Postgres table.
"""
from __future__ import annotations
import json
from services.vector_store import _get_collection
from database.connection import db_cursor
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage
from config import config

_llm = ChatOpenAI(model=config.LLM_MODEL, temperature=0, api_key=config.OPENAI_API_KEY)
_SAMPLE_WINDOW = 10


def _fetch_recent_bodies(user_id: str, relation: str) -> list[str]:
    """Pull up to _SAMPLE_WINDOW email bodies from Chroma for this (user, relation)."""
    col = _get_collection()
    results = col.get(
        where={"$and": [{"user_id": user_id}, {"relation": relation}]},
        include=["documents"],
        limit=_SAMPLE_WINDOW,
    )
    return results.get("documents", [])


def _analyse_style(bodies: list[str]) -> dict:
    joined = "\n\n---\n\n".join(bodies)
    prompt = f"""You are a writing-style analyst. Study these email samples (all from the same
user-relation context) and extract a reusable style profile.

EMAIL SAMPLES:
{joined}

Return ONLY valid JSON — no markdown fences:
{{
  "tone": "formal | semi-formal | casual | warm | professional",
  "formality": "high | medium | low",
  "avg_sentence_len": <integer>,
  "style_description": "2-3 sentences describing the overall writing style",
  "common_phrases": ["phrase1", "phrase2", "phrase3"],
  "style_examples": [
    {{"subject": "brief topic", "snippet": "1-2 representative sentences"}}
  ]
}}"""
    response = _llm.invoke([HumanMessage(content=prompt)])
    return json.loads(response.content.strip().replace("```json", "").replace("```", ""))


def upsert_writing_style(user_id: str, relation: str) -> None:
    """Re-analyse recent Chroma emails and upsert the style row in Postgres."""
    bodies = _fetch_recent_bodies(user_id, relation)
    if not bodies:
        return

    style = _analyse_style(bodies)

    with db_cursor() as cur:
        cur.execute(
            """
            INSERT INTO writing_styles
                (user_id, relation, tone, formality, avg_sentence_len,
                 style_description, common_phrases, style_examples,
                 sample_count, updated_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
            ON CONFLICT (user_id, relation) DO UPDATE SET
                tone               = EXCLUDED.tone,
                formality          = EXCLUDED.formality,
                avg_sentence_len   = EXCLUDED.avg_sentence_len,
                style_description  = EXCLUDED.style_description,
                common_phrases     = EXCLUDED.common_phrases,
                style_examples     = EXCLUDED.style_examples,
                sample_count       = EXCLUDED.sample_count,
                updated_at         = NOW()
            """,
            (
                user_id, relation,
                style.get("tone"),
                style.get("formality"),
                style.get("avg_sentence_len"),
                style.get("style_description"),
                json.dumps(style.get("common_phrases", [])),
                json.dumps(style.get("style_examples", [])),
                len(bodies),
            ),
        )


def get_writing_style(user_id: str, relation: str) -> dict | None:
    with db_cursor() as cur:
        cur.execute(
            """
            SELECT tone, formality, avg_sentence_len, style_description,
                   common_phrases, style_examples, sample_count
            FROM writing_styles
            WHERE user_id = %s AND relation = %s
            """,
            (user_id, relation),
        )
        row = cur.fetchone()
    return dict(row) if row else None
