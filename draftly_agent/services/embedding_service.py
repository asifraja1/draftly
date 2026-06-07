from openai import OpenAI
from config import config
import numpy as np

_client = OpenAI(api_key=config.OPENAI_API_KEY)


def embed(text: str) -> list[float]:
    """Return a unit-normalised embedding vector for the given text."""
    response = _client.embeddings.create(
        model=config.EMBEDDING_MODEL,
        input=text.strip(),
    )
    vec = response.data[0].embedding
    arr = np.array(vec, dtype=np.float32)
    norm = np.linalg.norm(arr)
    if norm > 0:
        arr = arr / norm
    return arr.tolist()


def cosine_similarity(a: list[float], b: list[float]) -> float:
    """Cosine similarity in [0, 1] between two normalised vectors."""
    va = np.array(a, dtype=np.float32)
    vb = np.array(b, dtype=np.float32)
    return float(np.dot(va, vb))
