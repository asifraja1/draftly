"""
Chroma vector store wrapper.

One Chroma collection is used for all email embeddings.
Each document is stored with metadata: user_id, relation, thread_id, from_email, subject.

The collection is persisted to disk at CHROMA_PATH (from .env).
"""
from __future__ import annotations
import os
import chromadb
from chromadb.config import Settings
from dotenv import load_dotenv

load_dotenv()

_CHROMA_PATH       = os.getenv("CHROMA_PATH", "./chroma_db")
_COLLECTION_NAME   = os.getenv("CHROMA_COLLECTION", "email_embeddings")

_client: chromadb.ClientAPI | None = None
_collection = None


def _get_collection():
    global _client, _collection
    if _collection is None:
        _client = chromadb.PersistentClient(
            path=_CHROMA_PATH,
            settings=Settings(anonymized_telemetry=False),
        )
        _collection = _client.get_or_create_collection(
            name=_COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},   # cosine similarity
        )
    return _collection


def save_embedding(
    doc_id:     str,
    embedding:  list[float],
    body:       str,
    user_id:    str,
    relation:   str,
    thread_id:  str,
    from_email: str = "",
    subject:    str = "",
) -> None:
    """Upsert a single email embedding into the Chroma collection."""
    col = _get_collection()
    col.upsert(
        ids=[doc_id],
        embeddings=[embedding],
        documents=[body],
        metadatas=[{
            "user_id":    user_id,
            "relation":   relation,
            "thread_id":  thread_id,
            "from_email": from_email,
            "subject":    subject,
        }],
    )


def search_similar(
    query_embedding: list[float],
    user_id:         str,
    relation:        str,
    top_k:           int = 1,
) -> list[dict]:
    """
    Return the top_k most similar emails for the given (user_id, relation).
    Each result: {body, similarity (0-100), metadata}
    """
    col = _get_collection()

    # Chroma returns distance (0 = identical for cosine space) — convert to similarity
    results = col.query(
        query_embeddings=[query_embedding],
        n_results=top_k,
        where={"$and": [{"user_id": user_id}, {"relation": relation}]},
        include=["documents", "distances", "metadatas"],
    )

    hits = []
    docs      = results.get("documents",  [[]])[0]
    distances = results.get("distances",  [[]])[0]
    metas     = results.get("metadatas",  [[]])[0]

    for doc, dist, meta in zip(docs, distances, metas):
        similarity = (1 - dist) * 100   # cosine distance → similarity %
        hits.append({
            "body":       doc,
            "similarity": similarity,
            "metadata":   meta,
        })

    return hits
