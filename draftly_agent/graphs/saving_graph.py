"""
LangGraph for EmailSavingAgent

Flow:  generate_embedding → save_to_chroma → update_writing_style → END
"""
import uuid
import operator
from typing import TypedDict, Annotated, List

from langgraph.graph import StateGraph, END

from services.embedding_service import embed
from services.vector_store import save_embedding
from services.writing_style_service import upsert_writing_style


# ── State ──────────────────────────────────────────────────────────────────────

class SavingState(TypedDict):
    user_id:    str
    relation:   str
    thread_id:  str
    from_email: str
    subject:    str
    body:       str
    metadata:   dict
    embedding:  List[float]
    errors:     Annotated[List[str], operator.add]


# ── Nodes ──────────────────────────────────────────────────────────────────────

def generate_embedding_node(state: SavingState) -> dict:
    try:
        return {"embedding": embed(state["body"])}
    except Exception as exc:
        return {"errors": [f"generate_embedding: {exc}"]}


def save_to_chroma_node(state: SavingState) -> dict:
    if not state.get("embedding"):
        return {"errors": ["save_to_chroma: no embedding produced"]}
    try:
        # Stable doc_id: thread + user so re-processing the same email upserts cleanly
        doc_id = f"{state['user_id']}_{state['thread_id']}_{uuid.uuid4().hex[:8]}"
        save_embedding(
            doc_id=doc_id,
            embedding=state["embedding"],
            body=state["body"],
            user_id=state["user_id"],
            relation=state["relation"],
            thread_id=state["thread_id"],
            from_email=state.get("from_email", ""),
            subject=state.get("subject", ""),
        )
        return {}
    except Exception as exc:
        return {"errors": [f"save_to_chroma: {exc}"]}


def update_writing_style_node(state: SavingState) -> dict:
    try:
        upsert_writing_style(state["user_id"], state["relation"])
        return {}
    except Exception as exc:
        return {"errors": [f"update_writing_style: {exc}"]}


# ── Routing ────────────────────────────────────────────────────────────────────

def has_embedding(state: SavingState) -> str:
    return "save" if state.get("embedding") else "end"


# ── Graph ──────────────────────────────────────────────────────────────────────

def build_saving_graph():
    g = StateGraph(SavingState)

    g.add_node("generate_embedding",   generate_embedding_node)
    g.add_node("save_to_chroma",       save_to_chroma_node)
    g.add_node("update_writing_style", update_writing_style_node)

    g.set_entry_point("generate_embedding")
    g.add_conditional_edges(
        "generate_embedding",
        has_embedding,
        {"save": "save_to_chroma", "end": END},
    )
    g.add_edge("save_to_chroma",       "update_writing_style")
    g.add_edge("update_writing_style", END)

    return g.compile()
